package com.mavenversion.mcp

import com.mavenversion.mcp.examples.MCPPlaywrightExample
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
        // Server initialization will be implemented in later tasks
    }
}
