package com.mavenversion.mcp.server

import com.mavenversion.mcp.client.MCPProcessManager
import com.mavenversion.mcp.client.PlaywrightMCPClient
import com.mavenversion.mcp.config.ApplicationConfig
import com.mavenversion.mcp.files.GradleFileManager
import com.mavenversion.mcp.files.MavenFileManager
import com.mavenversion.mcp.files.ProjectFileDetector
import com.mavenversion.mcp.reliability.ReliabilityService
import com.mavenversion.mcp.service.ErrorHandlingService
import com.mavenversion.mcp.tools.GetAllVersionsTool
import com.mavenversion.mcp.tools.GetLatestVersionTool
import com.mavenversion.mcp.tools.SearchDependencyTool
import com.mavenversion.mcp.tools.UpdateGradleDependencyTool
import com.mavenversion.mcp.tools.UpdateMavenDependencyTool
import com.mavenversion.mcp.web.MavenRepositoryClient
import mu.KotlinLogging

private val log = KotlinLogging.logger {}

/**
 * Factory for creating and configuring MCP server instances
 * Handles dependency injection and tool registration
 */
class MCPServerFactory {
    /**
     * Create a fully configured MCP server with all tools registered
     */
    fun createServer(config: ApplicationConfig = ApplicationConfig()): MCPServer {
        log.info { "Creating MCP server with configuration: ${config.serverName} v${config.serverVersion}" }

        try {
            // Create core components
            val toolRegistry = ToolRegistry()
            val projectFileDetector = ProjectFileDetector()
            val mavenFileManager = MavenFileManager()
            val gradleFileManager = GradleFileManager()
            val errorHandlingService = ErrorHandlingService()

            // Create MCP client components with configuration
            val mcpProcessManager = MCPProcessManager()
            val playwrightMCPClient = PlaywrightMCPClient()

            // Create reliability service with configuration
            val reliabilityService = ReliabilityService()

            // Create web client with configuration
            val mavenRepositoryClient =
                MavenRepositoryClient(
                    playwrightClient = playwrightMCPClient,
                    reliabilityService = reliabilityService,
                    baseUrl = config.baseUrl,
                )

            // Create and register all tools
            val searchTool = SearchDependencyTool(mavenRepositoryClient)
            val getLatestVersionTool = GetLatestVersionTool(mavenRepositoryClient)
            val getAllVersionsTool = GetAllVersionsTool(mavenRepositoryClient)
            val updateMavenTool = UpdateMavenDependencyTool(mavenRepositoryClient, projectFileDetector, mavenFileManager)
            val updateGradleTool = UpdateGradleDependencyTool(mavenRepositoryClient, projectFileDetector, gradleFileManager)

            // Register tools with the registry
            toolRegistry.registerTool(searchTool)
            toolRegistry.registerTool(getLatestVersionTool)
            toolRegistry.registerTool(getAllVersionsTool)
            toolRegistry.registerTool(updateMavenTool)
            toolRegistry.registerTool(updateGradleTool)

            log.info { "Successfully registered ${toolRegistry.getToolCount()} MCP tools" }
            log.info { "Server configured with base URL: ${config.baseUrl}" }

            // Create and return the server
            return MCPServer(toolRegistry)
        } catch (e: Exception) {
            log.error(e) { "Failed to create MCP server" }
            throw e
        }
    }

    /**
     * Create a minimal MCP server for testing
     */
    fun createTestServer(): MCPServer {
        log.info { "Creating test MCP server" }

        val toolRegistry = ToolRegistry()
        // For testing, we might not register all tools or use mocks
        return MCPServer(toolRegistry)
    }
}
