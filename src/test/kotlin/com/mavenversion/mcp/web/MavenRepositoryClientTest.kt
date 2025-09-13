package com.mavenversion.mcp.web

import com.mavenversion.mcp.client.PlaywrightMCPClient
import com.mavenversion.mcp.models.Dependency
import com.mavenversion.mcp.models.SearchResult
import com.mavenversion.mcp.reliability.PlaywrightMCPException
import com.mavenversion.mcp.reliability.ReliabilityService
import io.mockk.Runs
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@DisplayName("Maven Repository Client Tests")
class MavenRepositoryClientTest {
    private lateinit var mockPlaywrightClient: PlaywrightMCPClient
    private lateinit var mavenRepositoryClient: MavenRepositoryClient

    @BeforeEach
    fun setUp() {
        mockPlaywrightClient = mockk<PlaywrightMCPClient>()
        mavenRepositoryClient =
            MavenRepositoryClient(
                playwrightClient = mockPlaywrightClient,
                reliabilityService = ReliabilityService(maxRetries = 2, baseDelayMs = 100, rateLimitDelayMs = 50),
            )
    }

    @AfterEach
    fun tearDown() {
        clearAllMocks()
    }

    @Nested
    @DisplayName("Initialization Tests")
    inner class InitializationTests {
        @Test
        @DisplayName("Should initialize successfully when Playwright MCP connects")
        fun shouldInitializeSuccessfully() =
            runTest {
                coEvery { mockPlaywrightClient.connect() } returns Result.success(Unit)

                val result = mavenRepositoryClient.initialize()

                assertThat(result.isSuccess).isTrue()
                coVerify { mockPlaywrightClient.connect() }
            }

        @Test
        @DisplayName("Should handle initialization failure")
        fun shouldHandleInitializationFailure() =
            runTest {
                val exception = PlaywrightMCPException("Connection failed")
                coEvery { mockPlaywrightClient.connect() } returns Result.failure(exception)

                val result = mavenRepositoryClient.initialize()

                assertThat(result.isFailure).isTrue()
                assertThat(result.exceptionOrNull()).isInstanceOf(PlaywrightMCPException::class.java)
            }
    }

    @Nested
    @DisplayName("Navigation Tests")
    inner class NavigationTests {
        @Test
        @DisplayName("Should navigate to homepage successfully")
        fun shouldNavigateToHomepageSuccessfully() =
            runTest {
                val expectedContent = "<html>Homepage content</html>"
                coEvery { mockPlaywrightClient.navigateToUrl("https://mvnrepository.com") } returns
                    Result.success(expectedContent)

                val result = mavenRepositoryClient.navigateToHomepage()

                assertThat(result.isSuccess).isTrue()
                assertThat(result.getOrNull()).isEqualTo(expectedContent)
                coVerify { mockPlaywrightClient.navigateToUrl("https://mvnrepository.com") }
            }

        @Test
        @DisplayName("Should navigate to dependency page successfully")
        fun shouldNavigateToDependencyPageSuccessfully() =
            runTest {
                val groupId = "org.springframework"
                val artifactId = "spring-core"
                val expectedUrl = "https://mvnrepository.com/artifact/$groupId/$artifactId"
                val expectedContent = "<html>Dependency page content</html>"

                coEvery { mockPlaywrightClient.navigateToUrl(expectedUrl) } returns Result.success(expectedContent)
                coEvery { mockPlaywrightClient.waitForElement(".im", 10000) } returns Result.success(Unit)
                coEvery { mockPlaywrightClient.getPageContent() } returns Result.success(expectedContent)

                val result = mavenRepositoryClient.navigateToDependencyPage(groupId, artifactId)

                assertThat(result.isSuccess).isTrue()
                assertThat(result.getOrNull()).isEqualTo(expectedContent)
                coVerify { mockPlaywrightClient.navigateToUrl(expectedUrl) }
                coVerify { mockPlaywrightClient.waitForElement(".im", 10000) }
            }
    }

    @Nested
    @DisplayName("Search Tests")
    inner class SearchTests {
        @Test
        @DisplayName("Should search dependencies and return parsed results")
        fun shouldSearchDependenciesAndReturnParsedResults() =
            runTest {
                val query = "spring-boot"
                val mockSearchResultParser = mockk<SearchResultParser>()
                val clientWithMockParser =
                    MavenRepositoryClient(
                        playwrightClient = mockPlaywrightClient,
                        searchResultParser = mockSearchResultParser,
                        reliabilityService = ReliabilityService(maxRetries = 2, baseDelayMs = 100, rateLimitDelayMs = 50),
                    )

                val htmlContent =
                    """
                    <div class="im">
                        <a href="/artifact/org.springframework.boot/spring-boot-starter">Spring Boot Starter</a>
                        <p>Core starter for Spring Boot applications</p>
                    </div>
                    """.trimIndent()

                val expectedSearchResult =
                    SearchResult(
                        dependencies =
                            listOf(
                                Dependency(
                                    groupId = "org.springframework.boot",
                                    artifactId = "spring-boot-starter",
                                    description = "Core starter for Spring Boot applications",
                                    url = "https://mvnrepository.com/artifact/org.springframework.boot/spring-boot-starter",
                                ),
                            ),
                        totalResults = 1,
                        query = query,
                    )

                coEvery { mockPlaywrightClient.navigateToUrl("https://mvnrepository.com") } returns
                    Result.success("<html>Homepage</html>")
                coEvery { mockPlaywrightClient.waitForElement("input[name='q']", 5000) } returns Result.success(Unit)
                coEvery { mockPlaywrightClient.fillField("input[name='q']", query) } returns Result.success(Unit)
                coEvery { mockPlaywrightClient.clickElement("input[type='submit']") } returns Result.success(Unit)
                coEvery { mockPlaywrightClient.waitForElement(".im", 10000) } returns Result.success(Unit)
                coEvery { mockPlaywrightClient.getPageContent() } returns Result.success(htmlContent)
                every { mockSearchResultParser.parseSearchResults(htmlContent, query) } returns expectedSearchResult

                val result = clientWithMockParser.searchDependencies(query)

                assertThat(result.isSuccess).isTrue()
                val searchResult = result.getOrNull()
                assertThat(searchResult).isNotNull()
                assertThat(searchResult!!.query).isEqualTo(query)
                assertThat(searchResult.dependencies).hasSize(1)
                assertThat(searchResult.dependencies[0].groupId).isEqualTo("org.springframework.boot")
                assertThat(searchResult.dependencies[0].artifactId).isEqualTo("spring-boot-starter")

                coVerify { mockPlaywrightClient.navigateToUrl("https://mvnrepository.com") }
                coVerify { mockPlaywrightClient.fillField("input[name='q']", query) }
                coVerify { mockPlaywrightClient.clickElement("input[type='submit']") }
                verify { mockSearchResultParser.parseSearchResults(htmlContent, query) }
            }

        @Test
        @DisplayName("Should handle empty search query")
        fun shouldHandleEmptySearchQuery() =
            runTest {
                val result = mavenRepositoryClient.searchDependencies("")

                assertThat(result.isSuccess).isTrue()
                val searchResult = result.getOrNull()
                assertThat(searchResult).isNotNull()
                assertThat(searchResult!!.dependencies).isEmpty()
                assertThat(searchResult.totalResults).isEqualTo(0)
                assertThat(searchResult.query).isEmpty()
            }

        @Test
        @DisplayName("Should handle search failures")
        fun shouldHandleSearchFailures() =
            runTest {
                val query = "invalid-query"
                val exception = PlaywrightMCPException("Search failed")

                coEvery { mockPlaywrightClient.navigateToUrl(any()) } returns Result.failure(exception)

                val result = mavenRepositoryClient.searchDependencies(query)

                assertThat(result.isFailure).isTrue()
            }

        @Test
        @DisplayName("Should handle no search results gracefully")
        fun shouldHandleNoSearchResultsGracefully() =
            runTest {
                val query = "nonexistent-dependency"
                val noResultsHtml = "<html><body>No results found</body></html>"

                coEvery { mockPlaywrightClient.navigateToUrl("https://mvnrepository.com") } returns
                    Result.success("<html>Homepage</html>")
                coEvery { mockPlaywrightClient.waitForElement("input[name='q']", 5000) } returns Result.success(Unit)
                coEvery { mockPlaywrightClient.fillField("input[name='q']", query) } returns Result.success(Unit)
                coEvery { mockPlaywrightClient.clickElement("input[type='submit']") } returns Result.success(Unit)
                coEvery { mockPlaywrightClient.waitForElement(".im", 10000) } throws PlaywrightMCPException("Element not found")
                coEvery { mockPlaywrightClient.getPageContent() } returns Result.success(noResultsHtml)

                val result = mavenRepositoryClient.searchDependencies(query)

                assertThat(result.isSuccess).isTrue()
                val searchResult = result.getOrNull()
                assertThat(searchResult).isNotNull()
                assertThat(searchResult!!.dependencies).isEmpty()
                assertThat(searchResult.query).isEqualTo(query)
            }

        @Test
        @DisplayName("Should search dependencies raw HTML successfully")
        fun shouldSearchDependenciesRawHtmlSuccessfully() =
            runTest {
                val query = "spring-boot"
                val expectedContent = "<html>Search results</html>"

                coEvery { mockPlaywrightClient.navigateToUrl("https://mvnrepository.com") } returns
                    Result.success("<html>Homepage</html>")
                coEvery { mockPlaywrightClient.waitForElement("input[name='q']", 5000) } returns Result.success(Unit)
                coEvery { mockPlaywrightClient.fillField("input[name='q']", query) } returns Result.success(Unit)
                coEvery { mockPlaywrightClient.clickElement("input[type='submit']") } returns Result.success(Unit)
                coEvery { mockPlaywrightClient.waitForElement(".im", 10000) } returns Result.success(Unit)
                coEvery { mockPlaywrightClient.getPageContent() } returns Result.success(expectedContent)

                val result = mavenRepositoryClient.searchDependenciesRaw(query)

                assertThat(result.isSuccess).isTrue()
                assertThat(result.getOrNull()).isEqualTo(expectedContent)

                coVerify { mockPlaywrightClient.navigateToUrl("https://mvnrepository.com") }
                coVerify { mockPlaywrightClient.fillField("input[name='q']", query) }
                coVerify { mockPlaywrightClient.clickElement("input[type='submit']") }
            }
    }

    @Nested
    @DisplayName("Element Interaction Tests")
    inner class ElementInteractionTests {
        @Test
        @DisplayName("Should click element successfully")
        fun shouldClickElementSuccessfully() =
            runTest {
                val selector = "button.submit"
                coEvery { mockPlaywrightClient.clickElement(selector) } returns Result.success(Unit)

                val result = mavenRepositoryClient.clickElement(selector)

                assertThat(result.isSuccess).isTrue()
                coVerify { mockPlaywrightClient.clickElement(selector) }
            }

        @Test
        @DisplayName("Should wait for element successfully")
        fun shouldWaitForElementSuccessfully() =
            runTest {
                val selector = ".loading"
                val timeout = 5000L
                coEvery { mockPlaywrightClient.waitForElement(selector, timeout) } returns Result.success(Unit)

                val result = mavenRepositoryClient.waitForElement(selector, timeout)

                assertThat(result.isSuccess).isTrue()
                coVerify { mockPlaywrightClient.waitForElement(selector, timeout) }
            }

        @Test
        @DisplayName("Should get text content successfully")
        fun shouldGetTextContentSuccessfully() =
            runTest {
                val selector = ".version-text"
                val expectedText = "1.2.3"
                coEvery { mockPlaywrightClient.getTextContent(selector) } returns Result.success(expectedText)

                val result = mavenRepositoryClient.getTextContent(selector)

                assertThat(result.isSuccess).isTrue()
                assertThat(result.getOrNull()).isEqualTo(expectedText)
                coVerify { mockPlaywrightClient.getTextContent(selector) }
            }
    }

    @Nested
    @DisplayName("Retry Logic Tests")
    inner class RetryLogicTests {
        @Test
        @DisplayName("Should retry on failure and eventually succeed")
        fun shouldRetryOnFailureAndEventuallySucceed() =
            runTest {
                val expectedContent = "<html>Success</html>"
                val exception = PlaywrightMCPException("Temporary failure")

                // First call fails, second call succeeds
                var callCount = 0
                coEvery { mockPlaywrightClient.navigateToUrl("https://mvnrepository.com") } answers {
                    callCount++
                    if (callCount == 1) Result.failure(exception) else Result.success(expectedContent)
                }

                val result = mavenRepositoryClient.navigateToHomepage()

                assertThat(result.isSuccess).isTrue()
                assertThat(result.getOrNull()).isEqualTo(expectedContent)
                coVerify(exactly = 2) { mockPlaywrightClient.navigateToUrl("https://mvnrepository.com") }
            }

        @Test
        @DisplayName("Should fail after max retries")
        fun shouldFailAfterMaxRetries() =
            runTest {
                val exception = PlaywrightMCPException("Persistent failure")
                coEvery { mockPlaywrightClient.navigateToUrl("https://mvnrepository.com") } returns
                    Result.failure(exception)

                val result = mavenRepositoryClient.navigateToHomepage()

                assertThat(result.isFailure).isTrue()
                coVerify(exactly = 2) { mockPlaywrightClient.navigateToUrl("https://mvnrepository.com") }
            }
    }

    @Nested
    @DisplayName("Lifecycle Tests")
    inner class LifecycleTests {
        @Test
        @DisplayName("Should close client and disconnect")
        fun shouldCloseClientAndDisconnect() =
            runTest {
                coEvery { mockPlaywrightClient.disconnect() } just Runs

                mavenRepositoryClient.close()

                coVerify { mockPlaywrightClient.disconnect() }
            }

        @Test
        @DisplayName("Should report connection status")
        fun shouldReportConnectionStatus() {
            every { mockPlaywrightClient.isConnected() } returns true

            val isConnected = mavenRepositoryClient.isConnected()

            assertThat(isConnected).isTrue()
            verify { mockPlaywrightClient.isConnected() }
        }
    }

    @Nested
    @DisplayName("Page Content Tests")
    inner class PageContentTests {
        @Test
        @DisplayName("Should get current page content successfully")
        fun shouldGetCurrentPageContentSuccessfully() =
            runTest {
                val expectedContent = "<html>Current page</html>"
                coEvery { mockPlaywrightClient.getPageContent() } returns Result.success(expectedContent)

                val result = mavenRepositoryClient.getCurrentPageContent()

                assertThat(result.isSuccess).isTrue()
                assertThat(result.getOrNull()).isEqualTo(expectedContent)
                coVerify { mockPlaywrightClient.getPageContent() }
            }
    }

    @Nested
    @DisplayName("Version Retrieval Tests")
    inner class VersionRetrievalTests {
        @Test
        @DisplayName("Should get latest version successfully")
        fun shouldGetLatestVersionSuccessfully() =
            runTest {
                val groupId = "org.springframework.boot"
                val artifactId = "spring-boot-starter"
                val expectedUrl = "https://mvnrepository.com/artifact/$groupId/$artifactId"
                val htmlContent =
                    """
                    <table class="grid">
                        <tbody>
                            <tr>
                                <td><a href="/artifact/$groupId/$artifactId/3.2.1">3.2.1</a></td>
                                <td>Dec 21, 2023</td>
                                <td>1.2M</td>
                            </tr>
                        </tbody>
                    </table>
                    """.trimIndent()

                coEvery { mockPlaywrightClient.navigateToUrl(expectedUrl) } returns Result.success(htmlContent)
                coEvery { mockPlaywrightClient.waitForElement(".im", 10000) } returns Result.success(Unit)
                coEvery { mockPlaywrightClient.getPageContent() } returns Result.success(htmlContent)

                val result = mavenRepositoryClient.getLatestVersion(groupId, artifactId)

                assertThat(result.isSuccess).isTrue()
                val version = result.getOrNull()
                assertThat(version).isNotNull()
                assertThat(version!!.version).isEqualTo("3.2.1")
                assertThat(version.isLatest).isTrue()
                assertThat(version.releaseDate).isEqualTo("2023-12-21")

                coVerify { mockPlaywrightClient.navigateToUrl(expectedUrl) }
                coVerify { mockPlaywrightClient.waitForElement(".im", 10000) }
                coVerify { mockPlaywrightClient.getPageContent() }
            }

        @Test
        @DisplayName("Should get all versions successfully")
        fun shouldGetAllVersionsSuccessfully() =
            runTest {
                val groupId = "org.junit.jupiter"
                val artifactId = "junit-jupiter"
                val expectedUrl = "https://mvnrepository.com/artifact/$groupId/$artifactId"
                val htmlContent =
                    """
                    <table class="grid">
                        <tbody>
                            <tr>
                                <td><a href="/artifact/$groupId/$artifactId/5.10.1">5.10.1</a></td>
                                <td>2023-10-12</td>
                                <td>2.1M</td>
                            </tr>
                            <tr>
                                <td><a href="/artifact/$groupId/$artifactId/5.10.0">5.10.0</a></td>
                                <td>2023-07-23</td>
                                <td>1.8M</td>
                            </tr>
                            <tr>
                                <td><a href="/artifact/$groupId/$artifactId/5.9.3">5.9.3</a></td>
                                <td>2023-04-18</td>
                                <td>1.5M</td>
                            </tr>
                        </tbody>
                    </table>
                    """.trimIndent()

                coEvery { mockPlaywrightClient.navigateToUrl(expectedUrl) } returns Result.success(htmlContent)
                coEvery { mockPlaywrightClient.waitForElement(".im", 10000) } returns Result.success(Unit)
                coEvery { mockPlaywrightClient.getPageContent() } returns Result.success(htmlContent)

                val result = mavenRepositoryClient.getAllVersions(groupId, artifactId)

                assertThat(result.isSuccess).isTrue()
                val versions = result.getOrNull()
                assertThat(versions).isNotNull()
                assertThat(versions!!).hasSize(3)
                assertThat(versions[0].version).isEqualTo("5.10.1")
                assertThat(versions[0].isLatest).isTrue()
                assertThat(versions[1].version).isEqualTo("5.10.0")
                assertThat(versions[1].isLatest).isFalse()
                assertThat(versions[2].version).isEqualTo("5.9.3")
                assertThat(versions[2].isLatest).isFalse()

                coVerify { mockPlaywrightClient.navigateToUrl(expectedUrl) }
                coVerify { mockPlaywrightClient.waitForElement(".im", 10000) }
                coVerify { mockPlaywrightClient.getPageContent() }
            }

        @Test
        @DisplayName("Should handle version retrieval failure")
        fun shouldHandleVersionRetrievalFailure() =
            runTest {
                val groupId = "com.example"
                val artifactId = "nonexistent"
                val exception = PlaywrightMCPException("Dependency not found")

                coEvery { mockPlaywrightClient.navigateToUrl(any()) } returns Result.failure(exception)

                val latestResult = mavenRepositoryClient.getLatestVersion(groupId, artifactId)
                val allVersionsResult = mavenRepositoryClient.getAllVersions(groupId, artifactId)

                assertThat(latestResult.isFailure).isTrue()
                assertThat(allVersionsResult.isFailure).isTrue()
            }

        @Test
        @DisplayName("Should return null for latest version when no versions found")
        fun shouldReturnNullForLatestVersionWhenNoVersionsFound() =
            runTest {
                val groupId = "com.example"
                val artifactId = "empty"
                val htmlContent = "<html><body><p>No versions available</p></body></html>"

                coEvery { mockPlaywrightClient.navigateToUrl(any()) } returns Result.success(htmlContent)
                coEvery { mockPlaywrightClient.waitForElement(".im", 10000) } returns Result.success(Unit)
                coEvery { mockPlaywrightClient.getPageContent() } returns Result.success(htmlContent)

                val result = mavenRepositoryClient.getLatestVersion(groupId, artifactId)

                assertThat(result.isSuccess).isTrue()
                assertThat(result.getOrNull()).isNull()
            }

        @Test
        @DisplayName("Should return empty list for all versions when no versions found")
        fun shouldReturnEmptyListForAllVersionsWhenNoVersionsFound() =
            runTest {
                val groupId = "com.example"
                val artifactId = "empty"
                val htmlContent = "<html><body><p>No versions available</p></body></html>"

                coEvery { mockPlaywrightClient.navigateToUrl(any()) } returns Result.success(htmlContent)
                coEvery { mockPlaywrightClient.waitForElement(".im", 10000) } returns Result.success(Unit)
                coEvery { mockPlaywrightClient.getPageContent() } returns Result.success(htmlContent)

                val result = mavenRepositoryClient.getAllVersions(groupId, artifactId)

                assertThat(result.isSuccess).isTrue()
                val versions = result.getOrNull()
                assertThat(versions).isNotNull()
                assertThat(versions!!).isEmpty()
            }
    }
}
