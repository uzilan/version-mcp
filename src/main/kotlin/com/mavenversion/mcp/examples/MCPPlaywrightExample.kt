package com.mavenversion.mcp.examples

import com.mavenversion.mcp.client.PlaywrightMCPClient
import com.mavenversion.mcp.web.MavenRepositoryClient
import kotlinx.coroutines.runBlocking
import mu.KotlinLogging

private val log = KotlinLogging.logger {}

/**
 * Example demonstrating how to use the Playwright MCP integration
 *
 * This example shows how to:
 * 1. Connect to a Playwright MCP server
 * 2. Use the MavenRepositoryClient to interact with mvnrepository.com
 * 3. Handle errors and cleanup resources
 *
 * To run this example, you need:
 * 1. A Playwright MCP server running on localhost:3000
 * 2. The MCP server should expose playwright tools like:
 *    - playwright_navigate
 *    - playwright_click
 *    - playwright_fill
 *    - playwright_get_text
 *    - playwright_wait_for_selector
 *    - playwright_get_content
 */
object MCPPlaywrightExample {
    @JvmStatic
    fun main(args: Array<String>) =
        runBlocking {
            log.info { "Starting MCP Playwright Example" }

            val playwrightClient = PlaywrightMCPClient("http://localhost:3000")
            val mavenRepositoryClient = MavenRepositoryClient(playwrightClient)

            try {
                // Initialize the client and connect to MCP server
                log.info { "Initializing MavenRepositoryClient..." }
                val initResult = mavenRepositoryClient.initialize()

                if (initResult.isFailure) {
                    log.error { "Failed to initialize MCP client: ${initResult.exceptionOrNull()?.message}" }
                    log.info { "Make sure a Playwright MCP server is running on localhost:3000" }
                    return@runBlocking
                }

                log.info { "Successfully connected to Playwright MCP server" }

                // Example 1: Navigate to mvnrepository.com homepage
                log.info { "Navigating to mvnrepository.com homepage..." }
                val homepageResult = mavenRepositoryClient.navigateToHomepage()

                if (homepageResult.isSuccess) {
                    log.info { "Successfully navigated to homepage" }
                    val content = homepageResult.getOrNull()
                    log.debug { "Homepage content length: ${content?.length}" }
                } else {
                    log.error { "Failed to navigate to homepage: ${homepageResult.exceptionOrNull()?.message}" }
                }

                // Example 2: Search for Spring Boot dependencies and capture HTML
                log.info { "Searching for 'spring-boot' dependencies..." }
                val searchResultRaw = mavenRepositoryClient.searchDependenciesRaw("spring-boot")

                if (searchResultRaw.isSuccess) {
                    log.info { "Successfully performed search" }
                    val htmlContent = searchResultRaw.getOrNull()
                    log.info { "HTML content length: ${htmlContent?.length}" }

                    // Save HTML to file for analysis
                    htmlContent?.let { html ->
                        try {
                            val file = java.io.File("maven-search-results.html")
                            file.writeText(html)
                            log.info { "Saved HTML content to: ${file.absolutePath}" }

                            // Analyze patterns in the HTML
                            analyzeHtmlPatterns(html)
                        } catch (e: Exception) {
                            log.error(e) { "Failed to save HTML content" }
                        }
                    }
                } else {
                    log.error { "Failed to search dependencies: ${searchResultRaw.exceptionOrNull()?.message}" }
                }

                // Example 3: Navigate to a specific dependency page
                log.info { "Navigating to Spring Boot Starter Web dependency page..." }
                val dependencyResult =
                    mavenRepositoryClient.navigateToDependencyPage(
                        "org.springframework.boot",
                        "spring-boot-starter-web",
                    )

                if (dependencyResult.isSuccess) {
                    log.info { "Successfully navigated to dependency page" }
                    val dependencyContent = dependencyResult.getOrNull()
                    log.debug { "Dependency page content length: ${dependencyContent?.length}" }
                } else {
                    log.error { "Failed to navigate to dependency page: ${dependencyResult.exceptionOrNull()?.message}" }
                }

                log.info { "MCP Playwright Example completed successfully" }
            } catch (e: Exception) {
                log.error(e) { "Unexpected error during MCP Playwright Example" }
            } finally {
                // Always cleanup resources
                log.info { "Cleaning up resources..." }
                mavenRepositoryClient.close()
                log.info { "MCP Playwright Example finished" }
            }
        }

    private fun analyzeHtmlPatterns(html: String) {
        log.info { "=== HTML Pattern Analysis ===" }

        // Look for common patterns that might indicate search results
        val patterns =
            mapOf(
                "div elements" to Regex("""<div[^>]*class="[^"]*"[^>]*>""").findAll(html).count(),
                "anchor links" to Regex("""<a[^>]*href="[^"]*"[^>]*>""").findAll(html).count(),
                "artifact links" to Regex("""href="[^"]*artifact[^"]*"""").findAll(html).count(),
                "result containers" to Regex("""class="[^"]*result[^"]*"""").findAll(html).count(),
                "search items" to Regex("""class="[^"]*item[^"]*"""").findAll(html).count(),
            )

        patterns.forEach { (pattern, count) ->
            log.info { "  $pattern: $count" }
        }

        // Look for specific artifact-like patterns
        val artifactLinks = Regex("""href="(/artifact/[^"]+)"""").findAll(html)
        log.info { "  Sample artifact links found:" }
        artifactLinks.take(5).forEach { match ->
            log.info { "    ${match.groupValues[1]}" }
        }

        // Look for potential result count indicators
        val resultCountPatterns =
            listOf(
                Regex("""(\d+)\s+results?""", RegexOption.IGNORE_CASE),
                Regex("""of\s+(\d+)\s+results?""", RegexOption.IGNORE_CASE),
                Regex("""showing\s+\d+\s+to\s+\d+\s+of\s+(\d+)""", RegexOption.IGNORE_CASE),
            )

        resultCountPatterns.forEach { pattern ->
            val matches = pattern.findAll(html)
            if (matches.any()) {
                log.info { "  Found result count pattern: ${matches.first().value}" }
            }
        }

        // Look for common CSS classes that might contain results
        val classPatterns = Regex("""class="([^"]*(?:result|item|search|artifact|dependency)[^"]*)\"""", RegexOption.IGNORE_CASE)
        val uniqueClasses = classPatterns.findAll(html).map { it.groupValues[1] }.distinct().take(10)
        log.info { "  Potential result-related CSS classes:" }
        uniqueClasses.forEach { className ->
            log.info { "    class=\"$className\"" }
        }

        log.info { "=== End Analysis ===" }
    }
}
