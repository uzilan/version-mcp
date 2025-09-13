package com.mavenversion.mcp.web

import com.mavenversion.mcp.client.PlaywrightMCPClient
import com.mavenversion.mcp.client.PlaywrightMCPException
import kotlinx.coroutines.delay
import mu.KotlinLogging
import kotlin.math.min
import kotlin.math.pow

private val log = KotlinLogging.logger {}

/**
 * Client for interacting with mvnrepository.com using Playwright MCP
 */
class MavenRepositoryClient(
    private val playwrightClient: PlaywrightMCPClient = PlaywrightMCPClient(),
    private val baseUrl: String = "https://mvnrepository.com",
    private val maxRetries: Int = 3,
    private val baseDelayMs: Long = 1000,
) {
    /**
     * Initialize the client and connect to Playwright MCP server
     */
    suspend fun initialize(): Result<Unit> =
        runCatching {
            log.info { "Initializing MavenRepositoryClient with Playwright MCP" }
            playwrightClient.connect().getOrThrow()
            log.info { "Successfully connected to Playwright MCP server" }
        }.onFailure { error ->
            log.error(error) { "Failed to initialize MavenRepositoryClient" }
        }

    /**
     * Navigate to mvnrepository.com homepage
     */
    suspend fun navigateToHomepage(): Result<String> =
        executeWithRetry("navigate to homepage") {
            log.debug { "Navigating to mvnrepository.com homepage" }
            playwrightClient.navigateToUrl(baseUrl).getOrThrow()
        }

    /**
     * Search for dependencies on mvnrepository.com
     */
    suspend fun searchDependencies(query: String): Result<String> =
        executeWithRetry("search dependencies") {
            log.debug { "Searching for dependencies with query: $query" }

            // Navigate to homepage first
            playwrightClient.navigateToUrl(baseUrl).getOrThrow()

            // Wait for search input to be available
            playwrightClient.waitForElement("input[name='q']", 5000).getOrThrow()

            // Fill search query
            playwrightClient.fillField("input[name='q']", query).getOrThrow()

            // Submit search (either click search button or press enter)
            playwrightClient.clickElement("input[type='submit']").getOrThrow()

            // Wait for results to load
            playwrightClient.waitForElement(".im", 10000).getOrThrow()

            // Get the page content with search results
            playwrightClient.getPageContent().getOrThrow()
        }

    /**
     * Navigate to a specific dependency page
     */
    suspend fun navigateToDependencyPage(
        groupId: String,
        artifactId: String,
    ): Result<String> =
        executeWithRetry("navigate to dependency page") {
            val dependencyUrl = "$baseUrl/artifact/$groupId/$artifactId"
            log.debug { "Navigating to dependency page: $dependencyUrl" }

            playwrightClient.navigateToUrl(dependencyUrl).getOrThrow()

            // Wait for the dependency information to load
            playwrightClient.waitForElement(".im", 10000).getOrThrow()

            playwrightClient.getPageContent().getOrThrow()
        }

    /**
     * Get the current page content
     */
    suspend fun getCurrentPageContent(): Result<String> =
        executeWithRetry("get page content") {
            playwrightClient.getPageContent().getOrThrow()
        }

    /**
     * Click on a specific element
     */
    suspend fun clickElement(selector: String): Result<Unit> =
        executeWithRetry("click element") {
            log.debug { "Clicking element: $selector" }
            playwrightClient.clickElement(selector).getOrThrow()
        }

    /**
     * Wait for an element to appear
     */
    suspend fun waitForElement(
        selector: String,
        timeoutMs: Long = 5000,
    ): Result<Unit> =
        executeWithRetry("wait for element") {
            playwrightClient.waitForElement(selector, timeoutMs).getOrThrow()
        }

    /**
     * Get text content from a specific element
     */
    suspend fun getTextContent(selector: String): Result<String> =
        executeWithRetry("get text content") {
            playwrightClient.getTextContent(selector).getOrThrow()
        }

    /**
     * Close the client and disconnect from Playwright MCP
     */
    suspend fun close() {
        log.info { "Closing MavenRepositoryClient" }
        playwrightClient.disconnect()
    }

    /**
     * Check if the client is connected
     */
    fun isConnected(): Boolean = playwrightClient.isConnected()

    /**
     * Execute an operation with retry logic and exponential backoff
     */
    private suspend fun <T> executeWithRetry(
        operation: String,
        block: suspend () -> T,
    ): Result<T> =
        runCatching {
            var lastException: Exception? = null

            repeat(maxRetries) { attempt ->
                try {
                    return@runCatching block()
                } catch (e: PlaywrightMCPException) {
                    lastException = e
                    log.warn { "Attempt ${attempt + 1} failed for $operation: ${e.message}" }

                    if (attempt < maxRetries - 1) {
                        val delayMs = calculateBackoffDelay(attempt)
                        log.debug { "Retrying $operation in ${delayMs}ms" }
                        delay(delayMs)
                    }
                }
            }

            throw lastException ?: Exception("Unknown error during $operation")
        }.onFailure { error ->
            log.error(error) { "Failed to execute $operation after $maxRetries attempts" }
        }

    /**
     * Calculate exponential backoff delay
     */
    private fun calculateBackoffDelay(attempt: Int): Long {
        val exponentialDelay = baseDelayMs * 2.0.pow(attempt).toLong()
        val jitter = (Math.random() * 0.1 * exponentialDelay).toLong()
        return min(exponentialDelay + jitter, 30000) // Cap at 30 seconds
    }
}
