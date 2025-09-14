package com.mavenversion.mcp

import com.mavenversion.mcp.config.ApplicationConfig
import com.mavenversion.mcp.service.ApplicationService
import mu.KotlinLogging

private val log = KotlinLogging.logger {}

/**
 * Main entry point for the Maven Version MCP Server
 */
fun main(args: Array<String>) {
    try {
        // Parse command line arguments
        val config =
            if (args.contains("--help")) {
                ApplicationConfig.printUsage()
                return
            } else {
                ApplicationConfig.fromArgs(args)
            }

        // Create and start the application service
        val applicationService = ApplicationService(config)
        applicationService.start()
    } catch (e: Exception) {
        log.error(e) { "Failed to start application" }
        System.exit(1)
    }
}
