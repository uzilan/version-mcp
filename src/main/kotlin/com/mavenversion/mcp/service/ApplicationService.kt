package com.mavenversion.mcp.service

import com.mavenversion.mcp.config.ApplicationConfig
import com.mavenversion.mcp.examples.MCPPlaywrightExample
import com.mavenversion.mcp.server.MCPServer
import com.mavenversion.mcp.server.MCPServerFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import mu.KotlinLogging
import kotlin.system.exitProcess

private val log = KotlinLogging.logger {}

/**
 * Main application service that manages the complete lifecycle of the MCP server
 */
class ApplicationService(
    private val config: ApplicationConfig,
) {
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var mcpServer: MCPServer? = null
    private var isShuttingDown = false

    /**
     * Start the application
     */
    fun start() {
        log.info { "Starting Maven Version MCP Server v${config.serverVersion}" }
        log.info { "Protocol version: ${config.protocolVersion}" }
        log.info { "Log level: ${config.logLevel}" }

        try {
            if (config.enableExamples) {
                runExamples()
            } else {
                startMCPServer()
            }
        } catch (e: Exception) {
            log.error(e) { "Failed to start application" }
            shutdown(1)
        }
    }

    /**
     * Run examples instead of MCP server
     */
    private fun runExamples() {
        log.info { "Running Playwright example..." }
        try {
            MCPPlaywrightExample.main(emptyArray())
        } catch (e: Exception) {
            log.error(e) { "Example execution failed" }
            throw e
        }
    }

    /**
     * Start the MCP server
     */
    private fun startMCPServer() {
        log.info { "Initializing MCP server components..." }

        try {
            // Create server factory with configuration
            val serverFactory = MCPServerFactory()
            mcpServer = serverFactory.createServer()

            log.info { "MCP server created successfully" }

            // Start the server in a coroutine
            applicationScope.launch {
                try {
                    mcpServer?.runBlocking()
                } catch (e: Exception) {
                    log.error(e) { "MCP server execution failed" }
                    if (!isShuttingDown) {
                        shutdown(1)
                    }
                }
            }

            // Set up shutdown hooks
            setupShutdownHooks()

            log.info { "MCP server started successfully" }
            log.info { "Server is ready to accept MCP protocol connections" }

            // Keep the main thread alive
            runBlocking {
                while (!isShuttingDown) {
                    kotlinx.coroutines.delay(1000)
                }
            }
        } catch (e: Exception) {
            log.error(e) { "Failed to start MCP server" }
            throw e
        }
    }

    /**
     * Set up shutdown hooks for graceful termination
     */
    private fun setupShutdownHooks() {
        val shutdownHook =
            Thread {
                log.info { "Shutdown signal received" }
                shutdown(0)
            }

        Runtime.getRuntime().addShutdownHook(shutdownHook)

        // Handle SIGINT (Ctrl+C)
        System.setProperty("java.awt.headless", "true")
    }

    /**
     * Shutdown the application gracefully
     */
    fun shutdown(exitCode: Int = 0) {
        if (isShuttingDown) {
            log.warn { "Shutdown already in progress" }
            return
        }

        isShuttingDown = true
        log.info { "Initiating graceful shutdown..." }

        try {
            // Stop MCP server
            mcpServer?.let { server ->
                log.info { "Stopping MCP server..." }
                server.shutdown()
                log.info { "MCP server stopped" }
            }

            // Cancel application scope
            applicationScope.cancel()

            log.info { "Application shutdown complete" }
        } catch (e: Exception) {
            log.error(e) { "Error during shutdown" }
        } finally {
            exitProcess(exitCode)
        }
    }

    /**
     * Check if the application is running
     */
    fun isRunning(): Boolean {
        return !isShuttingDown && mcpServer?.isServerRunning() == true
    }

    /**
     * Get application status
     */
    fun getStatus(): ApplicationStatus {
        return ApplicationStatus(
            isRunning = isRunning(),
            isShuttingDown = isShuttingDown,
            serverRunning = mcpServer?.isServerRunning() ?: false,
            config = config,
        )
    }
}

/**
 * Application status information
 */
data class ApplicationStatus(
    val isRunning: Boolean,
    val isShuttingDown: Boolean,
    val serverRunning: Boolean,
    val config: ApplicationConfig,
)
