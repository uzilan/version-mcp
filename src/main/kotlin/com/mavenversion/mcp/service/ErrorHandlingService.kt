package com.mavenversion.mcp.service

import com.mavenversion.mcp.client.MCPContent
import com.mavenversion.mcp.client.MCPToolResponse
import com.mavenversion.mcp.reliability.MCPToolException
import mu.KotlinLogging

private val log = KotlinLogging.logger {}

/**
 * Service for handling errors across all service boundaries
 */
class ErrorHandlingService {
    /**
     * Handle exceptions and convert them to appropriate MCP responses
     */
    fun handleException(
        exception: Throwable,
        context: String = "Unknown",
    ): MCPToolResponse {
        log.error(exception) { "Error in $context" }

        return when (exception) {
            is MCPToolException -> {
                MCPToolResponse(
                    content =
                        listOf(
                            MCPContent(
                                type = "text",
                                text = "MCP Tool Error: ${exception.message}",
                            ),
                        ),
                    isError = true,
                )
            }
            is IllegalArgumentException -> {
                MCPToolResponse(
                    content =
                        listOf(
                            MCPContent(
                                type = "text",
                                text = "Invalid input: ${exception.message}",
                            ),
                        ),
                    isError = true,
                )
            }
            is SecurityException -> {
                MCPToolResponse(
                    content =
                        listOf(
                            MCPContent(
                                type = "text",
                                text = "Access denied: ${exception.message}",
                            ),
                        ),
                    isError = true,
                )
            }
            is java.io.IOException -> {
                MCPToolResponse(
                    content =
                        listOf(
                            MCPContent(
                                type = "text",
                                text = "I/O error: ${exception.message}",
                            ),
                        ),
                    isError = true,
                )
            }
            is java.net.UnknownHostException -> {
                MCPToolResponse(
                    content =
                        listOf(
                            MCPContent(
                                type = "text",
                                text = "Network error: Unable to connect to host",
                            ),
                        ),
                    isError = true,
                )
            }
            is java.net.SocketTimeoutException -> {
                MCPToolResponse(
                    content =
                        listOf(
                            MCPContent(
                                type = "text",
                                text = "Network timeout: Request took too long",
                            ),
                        ),
                    isError = true,
                )
            }
            is java.util.concurrent.TimeoutException -> {
                MCPToolResponse(
                    content =
                        listOf(
                            MCPContent(
                                type = "text",
                                text = "Operation timeout: ${exception.message}",
                            ),
                        ),
                    isError = true,
                )
            }
            is kotlinx.coroutines.CancellationException -> {
                MCPToolResponse(
                    content =
                        listOf(
                            MCPContent(
                                type = "text",
                                text = "Operation cancelled: ${exception.message}",
                            ),
                        ),
                    isError = true,
                )
            }
            else -> {
                MCPToolResponse(
                    content =
                        listOf(
                            MCPContent(
                                type = "text",
                                text = "Unexpected error: ${exception.message ?: "Unknown error occurred"}",
                            ),
                        ),
                    isError = true,
                )
            }
        }
    }

    /**
     * Handle validation errors
     */
    fun handleValidationError(message: String): MCPToolResponse {
        log.warn { "Validation error: $message" }
        return MCPToolResponse(
            content =
                listOf(
                    MCPContent(
                        type = "text",
                        text = "Validation error: $message",
                    ),
                ),
            isError = true,
        )
    }

    /**
     * Handle resource not found errors
     */
    fun handleNotFoundError(resource: String): MCPToolResponse {
        log.warn { "Resource not found: $resource" }
        return MCPToolResponse(
            content =
                listOf(
                    MCPContent(
                        type = "text",
                        text = "Resource not found: $resource",
                    ),
                ),
            isError = true,
        )
    }

    /**
     * Handle permission denied errors
     */
    fun handlePermissionError(resource: String): MCPToolResponse {
        log.warn { "Permission denied for resource: $resource" }
        return MCPToolResponse(
            content =
                listOf(
                    MCPContent(
                        type = "text",
                        text = "Permission denied: Cannot access $resource",
                    ),
                ),
            isError = true,
        )
    }

    /**
     * Handle network connectivity errors
     */
    fun handleNetworkError(
        url: String,
        cause: Throwable? = null,
    ): MCPToolResponse {
        log.error(cause) { "Network error accessing: $url" }
        return MCPToolResponse(
            content =
                listOf(
                    MCPContent(
                        type = "text",
                        text = "Network error: Unable to access $url. Please check your internet connection.",
                    ),
                ),
            isError = true,
        )
    }

    /**
     * Handle rate limiting errors
     */
    fun handleRateLimitError(
        service: String,
        retryAfter: Long? = null,
    ): MCPToolResponse {
        val retryMessage =
            if (retryAfter != null) {
                " Please retry after ${retryAfter}ms."
            } else {
                " Please retry later."
            }

        log.warn { "Rate limit exceeded for service: $service" }
        return MCPToolResponse(
            content =
                listOf(
                    MCPContent(
                        type = "text",
                        text = "Rate limit exceeded for $service.$retryMessage",
                    ),
                ),
            isError = true,
        )
    }

    /**
     * Handle parsing errors
     */
    fun handleParsingError(
        content: String,
        expectedFormat: String,
    ): MCPToolResponse {
        log.warn { "Parsing error: Expected $expectedFormat, got content of length ${content.length}" }
        return MCPToolResponse(
            content =
                listOf(
                    MCPContent(
                        type = "text",
                        text = "Parsing error: Unable to parse content as $expectedFormat",
                    ),
                ),
            isError = true,
        )
    }

    /**
     * Create a success response with content
     */
    fun createSuccessResponse(
        content: String,
        type: String = "text",
    ): MCPToolResponse {
        return MCPToolResponse(
            content =
                listOf(
                    MCPContent(
                        type = type,
                        text = content,
                    ),
                ),
            isError = false,
        )
    }

    /**
     * Create a success response with structured data
     */
    fun createSuccessResponseWithData(
        data: String,
        mimeType: String,
    ): MCPToolResponse {
        return MCPToolResponse(
            content =
                listOf(
                    MCPContent(
                        type = "data",
                        data = data,
                        mimeType = mimeType,
                    ),
                ),
            isError = false,
        )
    }
}
