package com.mavenversion.mcp.web

import com.mavenversion.mcp.client.PlaywrightMCPClient
import com.mavenversion.mcp.models.SearchResult
import com.mavenversion.mcp.models.Version
import com.mavenversion.mcp.reliability.CircuitBreaker
import com.mavenversion.mcp.reliability.PlaywrightMCPException
import com.mavenversion.mcp.reliability.ReliabilityService
import com.mavenversion.mcp.reliability.WebsiteStructureException
import mu.KotlinLogging

private val log = KotlinLogging.logger {}

/**
 * Client for interacting with mvnrepository.com using Playwright MCP with reliability features
 */
class MavenRepositoryClient(
    private val playwrightClient: PlaywrightMCPClient = PlaywrightMCPClient(),
    private val searchResultParser: SearchResultParser = SearchResultParser(),
    private val versionParser: VersionParser = VersionParser(),
    private val baseUrl: String = "https://mvnrepository.com",
    private val reliabilityService: ReliabilityService = ReliabilityService(),
    private val circuitBreaker: CircuitBreaker = CircuitBreaker(failureThreshold = 5, recoveryTimeMs = 60000),
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
        circuitBreaker.execute {
            reliabilityService.executeWithRetry(
                operation = "navigate to homepage",
                retryableExceptions = setOf(PlaywrightMCPException::class.java, Exception::class.java),
            ) {
                log.debug { "Navigating to mvnrepository.com homepage" }
                val content = playwrightClient.navigateToUrl(baseUrl).getOrThrow()

                content
            }.getOrThrow()
        }

    /**
     * Search for dependencies on mvnrepository.com and return parsed results
     */
    suspend fun searchDependencies(query: String): Result<SearchResult> =
        runCatching {
            log.debug { "Searching for dependencies with query: $query" }

            if (query.isBlank()) {
                log.warn { "Empty search query provided" }
                return@runCatching SearchResult(
                    dependencies = emptyList(),
                    totalResults = 0,
                    query = query,
                )
            }

            val html = searchDependenciesRaw(query).getOrThrow()
            searchResultParser.parseSearchResults(html, query)
        }.onFailure { error ->
            log.error(error) { "Failed to search dependencies for query: $query" }
        }

    /**
     * Search for dependencies on mvnrepository.com and return raw HTML
     */
    suspend fun searchDependenciesRaw(query: String): Result<String> =
        circuitBreaker.execute {
            reliabilityService.executeWithRetry(
                operation = "search dependencies",
                retryableExceptions = setOf(PlaywrightMCPException::class.java, WebsiteStructureException::class.java),
            ) {
                log.debug { "Searching for dependencies with query: $query" }

                // Navigate to homepage first
                playwrightClient.navigateToUrl(baseUrl).getOrThrow()

                // Wait for search input to be available with error handling
                try {
                    playwrightClient.waitForElement("input[name='q']", 5000).getOrThrow()
                } catch (e: PlaywrightMCPException) {
                    // Try waiting for search input again
                    try {
                        playwrightClient.waitForElement("input[name='q']", 3000).getOrThrow()
                    } catch (e2: PlaywrightMCPException) {
                        return@executeWithRetry reliabilityService.handleStructureChangeError(
                            "search dependencies",
                            "input[name='q']",
                            e2,
                        ).getOrThrow()
                    }
                }

                // Fill search query
                playwrightClient.fillField("input[name='q']", query).getOrThrow()

                // Submit search (either click search button or press enter)
                try {
                    playwrightClient.clickElement("input[type='submit']").getOrThrow()
                } catch (e: PlaywrightMCPException) {
                    // Try alternative submit methods
                    try {
                        playwrightClient.clickElement("button[type='submit']").getOrThrow()
                    } catch (e2: PlaywrightMCPException) {
                        return@executeWithRetry reliabilityService.handleStructureChangeError(
                            "search dependencies",
                            "submit button",
                            e2,
                        ).getOrThrow()
                    }
                }

                // Wait for results to load - check for either results or no results message
                try {
                    playwrightClient.waitForElement(".im", 10000).getOrThrow()
                } catch (e: PlaywrightMCPException) {
                    // Check if it's a "no results" page instead of a timeout
                    val content = playwrightClient.getPageContent().getOrThrow()
                    if (content.contains("No results found", ignoreCase = true) ||
                        content.contains("0 results", ignoreCase = true)
                    ) {
                        log.info { "No search results found for query: $query" }
                        return@executeWithRetry content
                    }

                    // Check for alternative result selectors
                    try {
                        playwrightClient.waitForElement(".search-results", 5000).getOrThrow()
                    } catch (e2: PlaywrightMCPException) {
                        return@executeWithRetry reliabilityService.handleStructureChangeError(
                            "search dependencies",
                            "search results",
                            e2,
                        ).getOrThrow()
                    }
                }

                // Get the page content with search results
                playwrightClient.getPageContent().getOrThrow()
            }.getOrThrow()
        }

    /**
     * Navigate to a specific dependency page
     */
    suspend fun navigateToDependencyPage(
        groupId: String,
        artifactId: String,
    ): Result<String> =
        circuitBreaker.execute {
            reliabilityService.executeWithRetry(
                operation = "navigate to dependency page",
                retryableExceptions = setOf(PlaywrightMCPException::class.java, WebsiteStructureException::class.java),
            ) {
                val dependencyUrl = "$baseUrl/artifact/$groupId/$artifactId"
                log.debug { "Navigating to dependency page: $dependencyUrl" }

                playwrightClient.navigateToUrl(dependencyUrl).getOrThrow()

                // Wait for the dependency information to load with fallback selectors
                try {
                    playwrightClient.waitForElement(".im", 10000).getOrThrow()
                } catch (e: PlaywrightMCPException) {
                    // Try alternative selectors for dependency pages
                    try {
                        playwrightClient.waitForElement(".artifact-info", 5000).getOrThrow()
                    } catch (e2: PlaywrightMCPException) {
                        try {
                            playwrightClient.waitForElement(".version-section", 5000).getOrThrow()
                        } catch (e3: PlaywrightMCPException) {
                            return@executeWithRetry reliabilityService.handleStructureChangeError(
                                "navigate to dependency page",
                                "dependency content",
                                e3,
                            ).getOrThrow()
                        }
                    }
                }

                playwrightClient.getPageContent().getOrThrow()
            }.getOrThrow()
        }

    /**
     * Get the current page content
     */
    suspend fun getCurrentPageContent(): Result<String> =
        circuitBreaker.execute {
            reliabilityService.executeWithRetry(
                operation = "get page content",
                retryableExceptions = setOf(PlaywrightMCPException::class.java),
            ) {
                playwrightClient.getPageContent().getOrThrow()
            }.getOrThrow()
        }

    /**
     * Click on a specific element
     */
    suspend fun clickElement(selector: String): Result<Unit> =
        circuitBreaker.execute {
            reliabilityService.executeWithRetry(
                operation = "click element",
                retryableExceptions = setOf(PlaywrightMCPException::class.java, WebsiteStructureException::class.java),
            ) {
                log.debug { "Clicking element: $selector" }
                try {
                    playwrightClient.clickElement(selector).getOrThrow()
                } catch (e: PlaywrightMCPException) {
                    reliabilityService.handleStructureChangeError("click element", selector, e).getOrThrow()
                }
            }.getOrThrow()
        }

    /**
     * Wait for an element to appear
     */
    suspend fun waitForElement(
        selector: String,
        timeoutMs: Long = 5000,
    ): Result<Unit> =
        circuitBreaker.execute {
            reliabilityService.executeWithRetry(
                operation = "wait for element",
                retryableExceptions = setOf(PlaywrightMCPException::class.java, WebsiteStructureException::class.java),
            ) {
                try {
                    playwrightClient.waitForElement(selector, timeoutMs).getOrThrow()
                } catch (e: PlaywrightMCPException) {
                    reliabilityService.handleStructureChangeError("wait for element", selector, e).getOrThrow()
                }
            }.getOrThrow()
        }

    /**
     * Get text content from a specific element
     */
    suspend fun getTextContent(selector: String): Result<String> =
        circuitBreaker.execute {
            reliabilityService.executeWithRetry(
                operation = "get text content",
                retryableExceptions = setOf(PlaywrightMCPException::class.java, WebsiteStructureException::class.java),
            ) {
                try {
                    playwrightClient.getTextContent(selector).getOrThrow()
                } catch (e: PlaywrightMCPException) {
                    reliabilityService.handleStructureChangeError("get text content", selector, e).getOrThrow()
                }
            }.getOrThrow()
        }

    /**
     * Get the latest version for a specific dependency
     */
    suspend fun getLatestVersion(
        groupId: String,
        artifactId: String,
    ): Result<Version?> =
        runCatching {
            log.debug { "Getting latest version for $groupId:$artifactId" }

            val html = navigateToDependencyPage(groupId, artifactId).getOrThrow()
            versionParser.parseLatestVersion(html, groupId, artifactId)
        }.onFailure { error ->
            log.error(error) { "Failed to get latest version for $groupId:$artifactId" }
        }

    /**
     * Get all versions for a specific dependency
     */
    suspend fun getAllVersions(
        groupId: String,
        artifactId: String,
    ): Result<List<Version>> =
        runCatching {
            log.debug { "Getting all versions for $groupId:$artifactId" }

            val html = navigateToDependencyPage(groupId, artifactId).getOrThrow()
            versionParser.parseAllVersions(html, groupId, artifactId)
        }.onFailure { error ->
            log.error(error) { "Failed to get all versions for $groupId:$artifactId" }
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
    suspend fun isConnected(): Boolean = playwrightClient.isConnected()
}
