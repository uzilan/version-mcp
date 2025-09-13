package com.mavenversion.mcp.client

import com.mavenversion.mcp.reliability.PlaywrightMCPException
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import mu.KotlinLogging

private val log = KotlinLogging.logger {}

/**
 * Specialized MCP client for Playwright operations
 */
class PlaywrightMCPClient(
    serverUrl: String = "http://localhost:3000",
) {
    private val mcpClient = MCPClient(serverUrl)

    /**
     * Connect to the Playwright MCP server
     */
    suspend fun connect(): Result<Unit> = mcpClient.connect()

    /**
     * Navigate to a URL using Playwright
     */
    suspend fun navigateToUrl(url: String): Result<String> =
        runCatching {
            log.debug { "Navigating to URL: $url" }

            val request =
                MCPToolRequest(
                    name = "playwright_navigate",
                    arguments =
                        mapOf<String, JsonElement>(
                            "url" to JsonPrimitive(url),
                        ),
                )

            val response = mcpClient.callTool(request).getOrThrow()

            if (response.isError) {
                throw PlaywrightMCPException("Navigation failed: ${response.content.firstOrNull()?.text}")
            }

            val pageContent =
                response.content.firstOrNull()?.text
                    ?: throw PlaywrightMCPException("No page content returned from navigation")

            log.debug { "Successfully navigated to $url, content length: ${pageContent.length}" }
            pageContent
        }.onFailure { error ->
            log.error(error) { "Failed to navigate to URL: $url" }
        }

    /**
     * Click an element using Playwright
     */
    suspend fun clickElement(selector: String): Result<Unit> =
        runCatching {
            log.debug { "Clicking element: $selector" }

            val request =
                MCPToolRequest(
                    name = "playwright_click",
                    arguments =
                        mapOf<String, JsonElement>(
                            "selector" to JsonPrimitive(selector),
                        ),
                )

            val response = mcpClient.callTool(request).getOrThrow()

            if (response.isError) {
                throw PlaywrightMCPException("Click failed: ${response.content.firstOrNull()?.text}")
            }

            log.debug { "Successfully clicked element: $selector" }
        }.onFailure { error ->
            log.error(error) { "Failed to click element: $selector" }
        }

    /**
     * Fill a form field using Playwright
     */
    suspend fun fillField(
        selector: String,
        value: String,
    ): Result<Unit> =
        runCatching {
            log.debug { "Filling field: $selector with value: $value" }

            val request =
                MCPToolRequest(
                    name = "playwright_fill",
                    arguments =
                        mapOf<String, JsonElement>(
                            "selector" to JsonPrimitive(selector),
                            "value" to JsonPrimitive(value),
                        ),
                )

            val response = mcpClient.callTool(request).getOrThrow()

            if (response.isError) {
                throw PlaywrightMCPException("Fill failed: ${response.content.firstOrNull()?.text}")
            }

            log.debug { "Successfully filled field: $selector" }
        }.onFailure { error ->
            log.error(error) { "Failed to fill field: $selector" }
        }

    /**
     * Get text content from an element using Playwright
     */
    suspend fun getTextContent(selector: String): Result<String> =
        runCatching {
            log.debug { "Getting text content from: $selector" }

            val request =
                MCPToolRequest(
                    name = "playwright_get_text",
                    arguments =
                        mapOf<String, JsonElement>(
                            "selector" to JsonPrimitive(selector),
                        ),
                )

            val response = mcpClient.callTool(request).getOrThrow()

            if (response.isError) {
                throw PlaywrightMCPException("Get text failed: ${response.content.firstOrNull()?.text}")
            }

            val textContent =
                response.content.firstOrNull()?.text
                    ?: throw PlaywrightMCPException("No text content returned")

            log.debug { "Successfully retrieved text content: ${textContent.take(100)}..." }
            textContent
        }.onFailure { error ->
            log.error(error) { "Failed to get text content from: $selector" }
        }

    /**
     * Wait for an element to be visible using Playwright
     */
    suspend fun waitForElement(
        selector: String,
        timeoutMs: Long = 5000,
    ): Result<Unit> =
        runCatching {
            log.debug { "Waiting for element: $selector (timeout: ${timeoutMs}ms)" }

            val request =
                MCPToolRequest(
                    name = "playwright_wait_for_selector",
                    arguments =
                        mapOf<String, JsonElement>(
                            "selector" to JsonPrimitive(selector),
                            "timeout" to JsonPrimitive(timeoutMs),
                        ),
                )

            val response = mcpClient.callTool(request).getOrThrow()

            if (response.isError) {
                throw PlaywrightMCPException("Wait for element failed: ${response.content.firstOrNull()?.text}")
            }

            log.debug { "Element is now visible: $selector" }
        }.onFailure { error ->
            log.error(error) { "Failed to wait for element: $selector" }
        }

    /**
     * Get the current page HTML using Playwright
     */
    suspend fun getPageContent(): Result<String> =
        runCatching {
            log.debug { "Getting current page content" }

            val request =
                MCPToolRequest(
                    name = "playwright_get_content",
                    arguments = emptyMap<String, JsonElement>(),
                )

            val response = mcpClient.callTool(request).getOrThrow()

            if (response.isError) {
                throw PlaywrightMCPException("Get page content failed: ${response.content.firstOrNull()?.text}")
            }

            val pageContent =
                response.content.firstOrNull()?.text
                    ?: throw PlaywrightMCPException("No page content returned")

            log.debug { "Successfully retrieved page content, length: ${pageContent.length}" }
            pageContent
        }.onFailure { error ->
            log.error(error) { "Failed to get page content" }
        }

    /**
     * Disconnect from the Playwright MCP server
     */
    suspend fun disconnect() {
        mcpClient.disconnect()
    }

    /**
     * Check if connected to the MCP server
     */
    fun isConnected(): Boolean = mcpClient.isConnected()
}


