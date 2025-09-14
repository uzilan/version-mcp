package com.mavenversion.mcp.tools

import com.mavenversion.mcp.client.MCPContent
import com.mavenversion.mcp.client.MCPProperty
import com.mavenversion.mcp.client.MCPSchema
import com.mavenversion.mcp.client.MCPTool
import com.mavenversion.mcp.client.MCPToolRequest
import com.mavenversion.mcp.client.MCPToolResponse
import com.mavenversion.mcp.errors.ErrorCodes
import com.mavenversion.mcp.errors.ErrorTypes
import com.mavenversion.mcp.errors.createStructuredErrorResponse
import com.mavenversion.mcp.logging.StructuredLoggingService
import com.mavenversion.mcp.models.SearchResult
import com.mavenversion.mcp.recovery.ErrorRecoveryService
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
    private val loggingService: StructuredLoggingService,
    private val errorRecoveryService: ErrorRecoveryService,
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
        val requestId = loggingService.generateRequestId()
        val startTime = System.currentTimeMillis()

        return try {
            loggingService.logToolStart(
                toolName = "search_dependency",
                operation = "execute",
                parameters =
                    mapOf(
                        "query" to (request.arguments["query"]?.jsonPrimitive?.content ?: "null"),
                        "limit" to (request.arguments["limit"]?.jsonPrimitive?.content ?: "null"),
                    ),
            )

            // Validate and extract parameters
            val query = extractStringParameter(request, "query")
            val limit = extractIntParameter(request, "limit") ?: 20

            // Validate parameters
            validateParameters(query, limit)

            // Perform the search with error recovery
            val searchResult =
                errorRecoveryService.executeWithRetry(
                    operation = "search_dependency",
                    maxRetries = 3,
                    baseDelayMs = 1000,
                ) {
                    performSearch(query, limit)
                }

            val duration = System.currentTimeMillis() - startTime
            loggingService.logToolSuccess(
                toolName = "search_dependency",
                operation = "execute",
                duration = duration,
                resultSummary = "Found ${searchResult.dependencies.size} dependencies",
            )

            // Format response
            formatSuccessResponse(searchResult)
        } catch (e: IllegalArgumentException) {
            val duration = System.currentTimeMillis() - startTime
            loggingService.logToolError(
                toolName = "search_dependency",
                operation = "execute",
                error = e,
                duration = duration,
            )

            createStructuredErrorResponse("search_dependency", "execute")
                .errorCode(ErrorCodes.INVALID_PARAMETER)
                .errorType(ErrorTypes.VALIDATION_ERROR)
                .message("Invalid parameters: ${e.message}")
                .userMessage("Please check your search parameters and try again")
                .parameter("query", request.arguments["query"]?.jsonPrimitive?.content ?: "null")
                .parameter("limit", request.arguments["limit"]?.jsonPrimitive?.content ?: "null")
                .requestId(requestId)
                .build()
        } catch (e: java.net.UnknownHostException) {
            val duration = System.currentTimeMillis() - startTime
            loggingService.logToolError(
                toolName = "search_dependency",
                operation = "execute",
                error = e,
                duration = duration,
            )

            createStructuredErrorResponse("search_dependency", "execute")
                .errorCode(ErrorCodes.HOST_UNREACHABLE)
                .errorType(ErrorTypes.NETWORK_ERROR)
                .message("Unable to connect to mvnrepository.com")
                .userMessage("Please check your internet connection and try again")
                .detail("host", "mvnrepository.com")
                .requestId(requestId)
                .build()
        } catch (e: java.util.concurrent.TimeoutException) {
            val duration = System.currentTimeMillis() - startTime
            loggingService.logToolError(
                toolName = "search_dependency",
                operation = "execute",
                error = e,
                duration = duration,
            )

            createStructuredErrorResponse("search_dependency", "execute")
                .errorCode(ErrorCodes.CONNECTION_TIMEOUT)
                .errorType(ErrorTypes.NETWORK_ERROR)
                .message("Search request timed out")
                .userMessage("The search is taking longer than expected. Please try again.")
                .requestId(requestId)
                .build()
        } catch (e: Exception) {
            val duration = System.currentTimeMillis() - startTime
            loggingService.logToolError(
                toolName = "search_dependency",
                operation = "execute",
                error = e,
                duration = duration,
            )

            createStructuredErrorResponse("search_dependency", "execute")
                .errorCode(ErrorCodes.INTERNAL_ERROR)
                .errorType(ErrorTypes.INTERNAL_ERROR)
                .message("Search failed: ${e.message}")
                .userMessage("An unexpected error occurred while searching. Please try again.")
                .detail("errorType", e.javaClass.simpleName)
                .requestId(requestId)
                .build()
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
