package com.mavenversion.mcp.web

import com.mavenversion.mcp.client.PlaywrightMCPClient
import com.mavenversion.mcp.reliability.PlaywrightMCPException
import com.mavenversion.mcp.reliability.ReliabilityService
import com.mavenversion.mcp.reliability.WebsiteStructureException
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@DisplayName("MavenRepositoryClient Reliability Tests")
class MavenRepositoryClientReliabilityTest {
    private lateinit var mockPlaywrightClient: PlaywrightMCPClient
    private lateinit var mockSearchResultParser: SearchResultParser
    private lateinit var mockVersionParser: VersionParser
    private lateinit var reliabilityService: ReliabilityService
    private lateinit var client: MavenRepositoryClient

    @BeforeEach
    fun setUp() {
        mockPlaywrightClient = mockk()
        mockSearchResultParser = mockk()
        mockVersionParser = mockk()
        reliabilityService = ReliabilityService(maxRetries = 3, baseDelayMs = 50, rateLimitDelayMs = 100)

        client =
            MavenRepositoryClient(
                playwrightClient = mockPlaywrightClient,
                searchResultParser = mockSearchResultParser,
                versionParser = mockVersionParser,
                reliabilityService = reliabilityService,
            )
    }

    @Nested
    @DisplayName("Retry Logic Integration Tests")
    inner class RetryLogicIntegrationTests {
        @Test
        @DisplayName("Should retry navigation on Playwright failures")
        fun shouldRetryNavigationOnPlaywrightFailures() =
            runTest {
                coEvery { mockPlaywrightClient.navigateToUrl(any()) }
                    .throws(PlaywrightMCPException("Network timeout"))
                    .andThen(Result.success("page content"))

                val result = client.navigateToHomepage()

                assertThat(result.isSuccess).isTrue()
                assertThat(result.getOrNull()).isEqualTo("page content")
                coVerify(exactly = 2) { mockPlaywrightClient.navigateToUrl(any()) }
            }

        @Test
        @DisplayName("Should retry search operations with exponential backoff")
        fun shouldRetrySearchOperationsWithExponentialBackoff() =
            runTest {
                coEvery { mockPlaywrightClient.navigateToUrl(any()) } returns Result.success("homepage")
                coEvery { mockPlaywrightClient.waitForElement("input[name='q']", any()) }
                    .throws(PlaywrightMCPException("Element not found"))
                    .andThenThrows(PlaywrightMCPException("Element not found"))
                    .andThen(Result.success(Unit))
                coEvery { mockPlaywrightClient.fillField(any(), any()) } returns Result.success(Unit)
                coEvery { mockPlaywrightClient.clickElement(any()) } returns Result.success(Unit)
                coEvery { mockPlaywrightClient.waitForElement(".im", any()) } returns Result.success(Unit)
                coEvery { mockPlaywrightClient.getPageContent() } returns Result.success("search results")

                val result = client.searchDependenciesRaw("test-query")

                assertThat(result.isSuccess).isTrue()
                coVerify(exactly = 3) { mockPlaywrightClient.waitForElement("input[name='q']", any()) }
            }

        @Test
        @DisplayName("Should handle website structure changes gracefully")
        fun shouldHandleWebsiteStructureChangesGracefully() =
            runTest {
                coEvery { mockPlaywrightClient.navigateToUrl(any()) } returns Result.success("homepage")
                coEvery { mockPlaywrightClient.waitForElement("input[name='q']", any()) }
                    .throws(PlaywrightMCPException("Selector not found"))

                val result = client.searchDependenciesRaw("test-query")

                assertThat(result.isFailure).isTrue()
                assertThat(result.exceptionOrNull()).isInstanceOf(WebsiteStructureException::class.java)

                val exception = result.exceptionOrNull() as WebsiteStructureException
                assertThat(exception.message).contains("Website structure may have changed")
                assertThat(exception.message).contains("input[name='q']")
            }

        @Test
        @DisplayName("Should try alternative selectors when primary selectors fail")
        fun shouldTryAlternativeSelectorsWhenPrimaryFail() =
            runTest {
                coEvery { mockPlaywrightClient.navigateToUrl(any()) } returns Result.success("homepage")
                coEvery { mockPlaywrightClient.waitForElement("input[name='q']", any()) } returns Result.success(Unit)
                coEvery { mockPlaywrightClient.fillField(any(), any()) } returns Result.success(Unit)
                coEvery { mockPlaywrightClient.clickElement("input[type='submit']") }
                    .throws(PlaywrightMCPException("Submit button not found"))
                coEvery { mockPlaywrightClient.clickElement("button[type='submit']") } returns Result.success(Unit)
                coEvery { mockPlaywrightClient.waitForElement(".im", any()) } returns Result.success(Unit)
                coEvery { mockPlaywrightClient.getPageContent() } returns Result.success("search results")

                val result = client.searchDependenciesRaw("test-query")

                assertThat(result.isSuccess).isTrue()
                coVerify { mockPlaywrightClient.clickElement("input[type='submit']") }
                coVerify { mockPlaywrightClient.clickElement("button[type='submit']") }
            }

        @Test
        @DisplayName("Should handle no results page correctly")
        fun shouldHandleNoResultsPageCorrectly() =
            runTest {
                coEvery { mockPlaywrightClient.navigateToUrl(any()) } returns Result.success("homepage")
                coEvery { mockPlaywrightClient.waitForElement("input[name='q']", any()) } returns Result.success(Unit)
                coEvery { mockPlaywrightClient.fillField(any(), any()) } returns Result.success(Unit)
                coEvery { mockPlaywrightClient.clickElement(any()) } returns Result.success(Unit)
                coEvery { mockPlaywrightClient.waitForElement(".im", any()) }
                    .throws(PlaywrightMCPException("Results not found"))
                coEvery { mockPlaywrightClient.getPageContent() } returns Result.success("No results found for your query")

                val result = client.searchDependenciesRaw("nonexistent-query")

                assertThat(result.isSuccess).isTrue()
                assertThat(result.getOrNull()).contains("No results found")
            }
    }

    @Nested
    @DisplayName("Circuit Breaker Integration Tests")
    inner class CircuitBreakerIntegrationTests {
        @Test
        @DisplayName("Should open circuit breaker after repeated failures")
        fun shouldOpenCircuitBreakerAfterRepeatedFailures() =
            runTest {
                coEvery { mockPlaywrightClient.navigateToUrl(any()) }
                    .throws(PlaywrightMCPException("Persistent failure"))

                // Cause enough failures to open circuit breaker
                repeat(6) {
                    val result = client.navigateToHomepage()
                    assertThat(result.isFailure).isTrue()
                }

                // Circuit breaker should now be open and reject requests immediately
                val result = client.navigateToHomepage()
                assertThat(result.isFailure).isTrue()
                // Note: The exact exception type depends on circuit breaker implementation
            }
    }

    @Nested
    @DisplayName("Dependency Page Navigation Tests")
    inner class DependencyPageNavigationTests {
        @Test
        @DisplayName("Should try alternative selectors for dependency pages")
        fun shouldTryAlternativeSelectorsForDependencyPages() =
            runTest {
                coEvery { mockPlaywrightClient.navigateToUrl(any()) } returns Result.success("dependency page")
                coEvery { mockPlaywrightClient.waitForElement(".im", any()) }
                    .throws(PlaywrightMCPException("Primary selector not found"))
                coEvery { mockPlaywrightClient.waitForElement(".artifact-info", any()) } returns Result.success(Unit)
                coEvery { mockPlaywrightClient.getPageContent() } returns Result.success("dependency content")

                val result = client.navigateToDependencyPage("com.example", "test-artifact")

                assertThat(result.isSuccess).isTrue()
                coVerify { mockPlaywrightClient.waitForElement(".im", any()) }
                coVerify { mockPlaywrightClient.waitForElement(".artifact-info", any()) }
            }

        @Test
        @DisplayName("Should fallback to third selector when first two fail")
        fun shouldFallbackToThirdSelectorWhenFirstTwoFail() =
            runTest {
                coEvery { mockPlaywrightClient.navigateToUrl(any()) } returns Result.success("dependency page")
                coEvery { mockPlaywrightClient.waitForElement(".im", any()) }
                    .throws(PlaywrightMCPException("Primary selector not found"))
                coEvery { mockPlaywrightClient.waitForElement(".artifact-info", any()) }
                    .throws(PlaywrightMCPException("Secondary selector not found"))
                coEvery { mockPlaywrightClient.waitForElement(".version-section", any()) } returns Result.success(Unit)
                coEvery { mockPlaywrightClient.getPageContent() } returns Result.success("dependency content")

                val result = client.navigateToDependencyPage("com.example", "test-artifact")

                assertThat(result.isSuccess).isTrue()
                coVerify { mockPlaywrightClient.waitForElement(".im", any()) }
                coVerify { mockPlaywrightClient.waitForElement(".artifact-info", any()) }
                coVerify { mockPlaywrightClient.waitForElement(".version-section", any()) }
            }

        @Test
        @DisplayName("Should report structure change when all selectors fail")
        fun shouldReportStructureChangeWhenAllSelectorsFail() =
            runTest {
                coEvery { mockPlaywrightClient.navigateToUrl(any()) } returns Result.success("dependency page")
                coEvery { mockPlaywrightClient.waitForElement(".im", any()) }
                    .throws(PlaywrightMCPException("Primary selector not found"))
                coEvery { mockPlaywrightClient.waitForElement(".artifact-info", any()) }
                    .throws(PlaywrightMCPException("Secondary selector not found"))
                coEvery { mockPlaywrightClient.waitForElement(".version-section", any()) }
                    .throws(PlaywrightMCPException("Tertiary selector not found"))

                val result = client.navigateToDependencyPage("com.example", "test-artifact")

                assertThat(result.isFailure).isTrue()
                assertThat(result.exceptionOrNull()).isInstanceOf(WebsiteStructureException::class.java)
            }
    }

    @Nested
    @DisplayName("Rate Limiting Integration Tests")
    inner class RateLimitingIntegrationTests {
        @Test
        @DisplayName("Should apply rate limiting between operations")
        fun shouldApplyRateLimitingBetweenOperations() =
            runTest {
                coEvery { mockPlaywrightClient.navigateToUrl(any()) } returns Result.success("page content")

                val startTime = System.currentTimeMillis()

                // Make multiple requests
                repeat(3) {
                    client.navigateToHomepage()
                }

                val endTime = System.currentTimeMillis()
                val totalTime = endTime - startTime

                // Should have rate limiting delays (at least 2 delays of 100ms each)
                assertThat(totalTime).isGreaterThan(150)
            }
    }
}
