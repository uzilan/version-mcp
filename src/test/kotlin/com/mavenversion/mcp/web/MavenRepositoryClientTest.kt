package com.mavenversion.mcp.web

import com.mavenversion.mcp.client.PlaywrightMCPClient
import com.mavenversion.mcp.client.PlaywrightMCPException
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
                maxRetries = 2,
                baseDelayMs = 100,
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
        @DisplayName("Should search dependencies successfully")
        fun shouldSearchDependenciesSuccessfully() =
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

                val result = mavenRepositoryClient.searchDependencies(query)

                assertThat(result.isSuccess).isTrue()
                assertThat(result.getOrNull()).isEqualTo(expectedContent)

                coVerify { mockPlaywrightClient.navigateToUrl("https://mvnrepository.com") }
                coVerify { mockPlaywrightClient.fillField("input[name='q']", query) }
                coVerify { mockPlaywrightClient.clickElement("input[type='submit']") }
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
}
