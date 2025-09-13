package com.mavenversion.mcp.client

import kotlinx.serialization.Serializable

/**
 * Configuration for MCP server connection
 */
@Serializable
data class MCPServerConfig(
    val name: String,
    val command: List<String>,
    val args: List<String> = emptyList(),
    val env: Map<String, String> = emptyMap(),
    val workingDirectory: String? = null,
    val autoRestart: Boolean = true,
    val maxRestartAttempts: Int = 3,
    val restartDelayMs: Long = 1000
) {
    /**
     * Get the full command including arguments
     */
    fun getFullCommand(): List<String> = command + args
    
    companion object {
        /**
         * Default configuration for Playwright MCP server using uvx
         */
        fun playwrightDefault() = MCPServerConfig(
            name = "playwright",
            command = listOf("uvx", "playwright-mcp-server"),
            env = mapOf(
                "FASTMCP_LOG_LEVEL" to "ERROR"
            )
        )
        
        /**
         * Default configuration for Playwright MCP server using npx
         */
        fun playwrightNpx() = MCPServerConfig(
            name = "playwright",
            command = listOf("npx", "@modelcontextprotocol/server-playwright"),
            env = mapOf(
                "NODE_ENV" to "production"
            )
        )
    }
}