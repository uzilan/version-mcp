package com.mavenversion.mcp.server

import com.mavenversion.mcp.client.MCPTool
import com.mavenversion.mcp.client.MCPToolRequest
import com.mavenversion.mcp.client.MCPToolResponse
import mu.KotlinLogging

private val log = KotlinLogging.logger {}

/**
 * Interface for MCP tools
 */
interface MCPToolInterface {
    fun getToolDefinition(): MCPTool

    suspend fun execute(request: MCPToolRequest): MCPToolResponse
}

/**
 * Registry for MCP tools and their management
 * Handles tool registration, discovery, and execution
 */
class ToolRegistry {
    private val tools = mutableMapOf<String, MCPToolInterface>()

    /**
     * Register a tool with the registry
     */
    fun registerTool(tool: MCPToolInterface) {
        val toolDefinition = tool.getToolDefinition()
        tools[toolDefinition.name] = tool
        log.info { "Registered MCP tool: ${toolDefinition.name}" }
    }

    /**
     * Get all registered tools
     */
    fun getAllTools(): List<MCPTool> {
        return tools.values.map { it.getToolDefinition() }
    }

    /**
     * Get a specific tool by name
     */
    fun getTool(name: String): MCPToolInterface? {
        return tools[name]
    }

    /**
     * Check if a tool is registered
     */
    fun hasTool(name: String): Boolean {
        return tools.containsKey(name)
    }

    /**
     * Execute a tool by name
     */
    suspend fun executeTool(
        name: String,
        request: MCPToolRequest,
    ): MCPToolResponse {
        val tool = tools[name]
        return if (tool != null) {
            log.debug { "Executing tool: $name with arguments: ${request.arguments}" }
            tool.execute(request)
        } else {
            log.warn { "Tool not found: $name" }
            MCPToolResponse(
                content =
                    listOf(
                        com.mavenversion.mcp.client.MCPContent(
                            type = "text",
                            text = "Tool '$name' not found. Available tools: ${tools.keys.joinToString(", ")}",
                        ),
                    ),
                isError = true,
            )
        }
    }

    /**
     * Get the number of registered tools
     */
    fun getToolCount(): Int {
        return tools.size
    }

    /**
     * Clear all registered tools
     */
    fun clear() {
        tools.clear()
        log.info { "Cleared all registered tools" }
    }
}
