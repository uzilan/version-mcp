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

                // Example 2: Search for Spring Boot dependencies
                log.info { "Searching for 'spring-boot' dependencies..." }
                val searchResult = mavenRepositoryClient.searchDependencies("spring-boot")

                if (searchResult.isSuccess) {
                    log.info { "Successfully performed search" }
                    val searchContent = searchResult.getOrNull()
                    log.debug { "Search results content length: ${searchContent?.length}" }
                } else {
                    log.error { "Failed to search dependencies: ${searchResult.exceptionOrNull()?.message}" }
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
}
