package com.mavenversion.mcp.client

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/**
 * MCP tool request
 */
@Serializable
data class MCPToolRequest(
    val name: String,
    val arguments: Map<String, JsonElement> = emptyMap(),
)

/**
 * MCP tool response
 */
@Serializable
data class MCPToolResponse(
    val content: List<MCPContent>,
    val isError: Boolean = false,
)

/**
 * MCP content item
 */
@Serializable
data class MCPContent(
    val type: String,
    val text: String? = null,
    val data: String? = null,
    val mimeType: String? = null,
)

/**
 * MCP tool definition
 */
@Serializable
data class MCPTool(
    val name: String,
    val description: String,
    val inputSchema: MCPSchema,
)

/**
 * MCP schema definition
 */
@Serializable
data class MCPSchema(
    val type: String,
    val properties: Map<String, MCPProperty> = emptyMap(),
    val required: List<String> = emptyList(),
)

/**
 * MCP property definition
 */
@Serializable
data class MCPProperty(
    val type: String,
    val description: String? = null,
    val enum: List<String>? = null,
)

/**
 * Response for listing tools
 */
@Serializable
data class MCPToolsListResponse(
    val tools: List<MCPTool>,
)
