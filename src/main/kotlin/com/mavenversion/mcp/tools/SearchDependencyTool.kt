package com.mavenversion.mcp.tools

import com.mavenversion.mcp.client.MCPContent
import com.mavenversion.mcp.client.MCPProperty
import com.mavenversion.mcp.client.MCPSchema
import com.mavenversion.mcp.client.MCPTool
import com.mavenversion.mcp.client.MCPToolRequest
import com.mavenversion.mcp.client.MCPToolResponse
import com.mavenversion.mcp.models.SearchResult
import com.mavenversion.mcp.server.MCPToolInterface
import com.mavenversion.mcp.web.MavenRepositoryClient
import kotlinx.serialization.json.jsonPrimitive
import mu.KotlinLogging

private val log = KotlinLogging.logger {}

/**
 * MCP tool for searching dependencies on mvnrepository.com
 * Implements Requirement 1: Search for Java dependencies by name
 */
class SearchDependencyTool(
    private val mavenRepositoryClient: MavenRepositoryClient,
) : MCPToolInterface {
    /**
     * Get the MCP tool definition
     */
    override fun getToolDefinition(): MCPTool {
        return MCPTool(
            name = "search_dependency",
            description = "Search for Java dependencies by name on mvnrepository.com",
            inputSchema =
                MCPSchema(
                    type = "object",
                    properties =
                        mapOf(
                            "query" to
                                MCPProperty(
                                    type = "string",
                                    description = "The search query (dependency name or keywords)",
                                ),
                            "limit" to
                                MCPProperty(
                                    type = "integer",
                                    description = "Maximum number of results to return (default: 20, max: 100)",
                                ),
                        ),
                    required = listOf("query"),
                ),
        )
    }

    /**
     * Execute the search dependency tool
     */
    override suspend fun execute(request: MCPToolRequest): MCPToolResponse {
        return try {
            log.debug { "Executing search dependency tool with request: $request" }

            // Validate and extract parameters
            val query = extractStringParameter(request, "query")
            val limit = extractIntParameter(request, "limit") ?: 20

            // Validate parameters
            validateParameters(query, limit)

            // Perform the search
            val searchResult = performSearch(query, limit)

            // Format response
            formatSuccessResponse(searchResult)
        } catch (e: IllegalArgumentException) {
            log.warn { "Invalid parameters for search dependency tool: ${e.message}" }
            formatErrorResponse("Invalid parameters: ${e.message}")
        } catch (e: Exception) {
            log.error(e) { "Error executing search dependency tool" }
            formatErrorResponse("Search failed: ${e.message}")
        }
    }

    /**
     * Extract string parameter from request
     */
    private fun extractStringParameter(
        request: MCPToolRequest,
        paramName: String,
    ): String {
        val value =
            request.arguments[paramName]?.jsonPrimitive?.content
                ?: throw IllegalArgumentException("Missing required parameter: $paramName")
        return value
    }

    /**
     * Extract optional integer parameter from request
     */
    private fun extractIntParameter(
        request: MCPToolRequest,
        paramName: String,
    ): Int? {
        return request.arguments[paramName]?.jsonPrimitive?.content?.toIntOrNull()
    }

    /**
     * Validate input parameters
     */
    private fun validateParameters(
        query: String,
        limit: Int,
    ) {
        if (query.isBlank()) {
            throw IllegalArgumentException("Search query cannot be empty")
        }
        if (query.length < 2) {
            throw IllegalArgumentException("Search query must be at least 2 characters long")
        }
        if (limit < 1 || limit > 100) {
            throw IllegalArgumentException("Limit must be between 1 and 100")
        }
    }

    /**
     * Perform the actual search operation
     */
    private suspend fun performSearch(
        query: String,
        limit: Int,
    ): SearchResult {
        log.info { "Searching for dependencies with query: '$query' (limit: $limit)" }

        val searchResult = mavenRepositoryClient.searchDependencies(query).getOrThrow()
        val limitedDependencies = searchResult.dependencies.take(limit)

        log.info { "Found ${limitedDependencies.size} dependencies for query: '$query'" }

        return SearchResult(
            dependencies = limitedDependencies,
            totalResults = limitedDependencies.size,
            query = query,
        )
    }

    /**
     * Format successful response
     */
    private fun formatSuccessResponse(searchResult: SearchResult): MCPToolResponse {
        val responseText =
            if (searchResult.dependencies.isEmpty()) {
                "No dependencies found for query: '${searchResult.query}'"
            } else {
                buildString {
                    appendLine("Found ${searchResult.totalResults} dependencies for query: '${searchResult.query}'")
                    appendLine()
                    searchResult.dependencies.forEachIndexed { index, dependency ->
                        appendLine("${index + 1}. ${dependency.groupId}:${dependency.artifactId}")
                        dependency.description?.let { desc ->
                            appendLine("   Description: $desc")
                        }
                        dependency.url?.let { url ->
                            appendLine("   URL: $url")
                        }
                        appendLine()
                    }
                }
            }

        return MCPToolResponse(
            content =
                listOf(
                    MCPContent(
                        type = "text",
                        text = responseText,
                    ),
                ),
            isError = false,
        )
    }

    /**
     * Format error response
     */
    private fun formatErrorResponse(message: String): MCPToolResponse {
        return MCPToolResponse(
            content =
                listOf(
                    MCPContent(
                        type = "text",
                        text = "Error: $message",
                    ),
                ),
            isError = true,
        )
    }
}
