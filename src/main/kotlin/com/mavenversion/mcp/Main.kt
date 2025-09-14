package com.mavenversion.mcp

import com.mavenversion.mcp.examples.MCPPlaywrightExample
import com.mavenversion.mcp.server.MCPServerFactory
import mu.KotlinLogging

private val log = KotlinLogging.logger {}

/**
 * Main entry point for the Maven Version MCP Server
 */
fun main(args: Array<String>) {
    if (args.isNotEmpty() && args[0] == "example") {
        log.info { "Running Playwright example..." }
        MCPPlaywrightExample.main(emptyArray())
    } else {
        log.info { "Maven Version MCP Server starting..." }

        try {
            // Create and start the MCP server
            val serverFactory = MCPServerFactory()
            val server = serverFactory.createServer()

            log.info { "Starting MCP server..." }
            server.runBlocking()
        } catch (e: Exception) {
            log.error(e) { "Failed to start MCP server" }
            System.exit(1)
        }
    }
}
