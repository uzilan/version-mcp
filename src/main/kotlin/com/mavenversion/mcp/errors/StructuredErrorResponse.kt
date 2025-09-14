package com.mavenversion.mcp.errors

import com.mavenversion.mcp.client.MCPContent
import com.mavenversion.mcp.client.MCPToolResponse
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import mu.KotlinLogging

private val log = KotlinLogging.logger {}
private val json = Json { prettyPrint = true }

/**
 * Structured error response for MCP tools
 */
@Serializable
data class StructuredErrorResponse(
    val error: ErrorDetails,
    val context: ErrorContext,
    val timestamp: String,
    val requestId: String? = null,
)

@Serializable
data class ErrorDetails(
    val code: String,
    val message: String,
    val type: String,
    val details: Map<String, String> = emptyMap(),
)

@Serializable
data class ErrorContext(
    val tool: String,
    val operation: String,
    val parameters: Map<String, String> = emptyMap(),
    val userMessage: String,
)

/**
 * Error codes for different types of failures
 */
object ErrorCodes {
    // Parameter validation errors
    const val INVALID_PARAMETER = "INVALID_PARAMETER"
    const val MISSING_PARAMETER = "MISSING_PARAMETER"
    const val PARAMETER_TOO_SHORT = "PARAMETER_TOO_SHORT"
    const val PARAMETER_TOO_LONG = "PARAMETER_TOO_LONG"
    const val INVALID_PARAMETER_FORMAT = "INVALID_PARAMETER_FORMAT"

    // Network and connectivity errors
    const val NETWORK_ERROR = "NETWORK_ERROR"
    const val CONNECTION_TIMEOUT = "CONNECTION_TIMEOUT"
    const val HOST_UNREACHABLE = "HOST_UNREACHABLE"
    const val DNS_RESOLUTION_FAILED = "DNS_RESOLUTION_FAILED"

    // MCP client errors
    const val MCP_CLIENT_ERROR = "MCP_CLIENT_ERROR"
    const val MCP_CONNECTION_FAILED = "MCP_CONNECTION_FAILED"
    const val MCP_TIMEOUT = "MCP_TIMEOUT"
    const val MCP_PROTOCOL_ERROR = "MCP_PROTOCOL_ERROR"

    // File system errors
    const val FILE_NOT_FOUND = "FILE_NOT_FOUND"
    const val FILE_ACCESS_DENIED = "FILE_ACCESS_DENIED"
    const val FILE_READ_ERROR = "FILE_READ_ERROR"
    const val FILE_WRITE_ERROR = "FILE_WRITE_ERROR"
    const val INVALID_FILE_FORMAT = "INVALID_FILE_FORMAT"

    // Dependency resolution errors
    const val DEPENDENCY_NOT_FOUND = "DEPENDENCY_NOT_FOUND"
    const val VERSION_NOT_FOUND = "VERSION_NOT_FOUND"
    const val INVALID_VERSION_FORMAT = "INVALID_VERSION_FORMAT"
    const val DEPENDENCY_RESOLUTION_FAILED = "DEPENDENCY_RESOLUTION_FAILED"

    // Project detection errors
    const val PROJECT_NOT_DETECTED = "PROJECT_NOT_DETECTED"
    const val UNSUPPORTED_PROJECT_TYPE = "UNSUPPORTED_PROJECT_TYPE"
    const val BUILD_FILE_NOT_FOUND = "BUILD_FILE_NOT_FOUND"
    const val BUILD_FILE_INVALID = "BUILD_FILE_INVALID"

    // Rate limiting and quota errors
    const val RATE_LIMIT_EXCEEDED = "RATE_LIMIT_EXCEEDED"
    const val QUOTA_EXCEEDED = "QUOTA_EXCEEDED"
    const val SERVICE_UNAVAILABLE = "SERVICE_UNAVAILABLE"

    // Internal errors
    const val INTERNAL_ERROR = "INTERNAL_ERROR"
    const val UNEXPECTED_ERROR = "UNEXPECTED_ERROR"
    const val RESOURCE_EXHAUSTED = "RESOURCE_EXHAUSTED"
}

/**
 * Error types for categorization
 */
object ErrorTypes {
    const val VALIDATION_ERROR = "VALIDATION_ERROR"
    const val NETWORK_ERROR = "NETWORK_ERROR"
    const val FILE_SYSTEM_ERROR = "FILE_SYSTEM_ERROR"
    const val DEPENDENCY_ERROR = "DEPENDENCY_ERROR"
    const val PROJECT_ERROR = "PROJECT_ERROR"
    const val RATE_LIMIT_ERROR = "RATE_LIMIT_ERROR"
    const val INTERNAL_ERROR = "INTERNAL_ERROR"
}

/**
 * Builder for creating structured error responses
 */
class StructuredErrorResponseBuilder(
    private val toolName: String,
    private val operation: String,
) {
    private var errorCode: String = ErrorCodes.INTERNAL_ERROR
    private var errorType: String = ErrorTypes.INTERNAL_ERROR
    private var message: String = "An unexpected error occurred"
    private var details: MutableMap<String, String> = mutableMapOf()
    private var parameters: MutableMap<String, String> = mutableMapOf()
    private var userMessage: String = "An error occurred while processing your request"
    private var requestId: String? = null

    fun errorCode(code: String) = apply { this.errorCode = code }

    fun errorType(type: String) = apply { this.errorType = type }

    fun message(message: String) = apply { this.message = message }

    fun userMessage(userMessage: String) = apply { this.userMessage = userMessage }

    fun detail(
        key: String,
        value: String,
    ) = apply { this.details[key] = value }

    fun parameter(
        key: String,
        value: String,
    ) = apply { this.parameters[key] = value }

    fun requestId(requestId: String) = apply { this.requestId = requestId }

    fun build(): MCPToolResponse {
        val timestamp = java.time.Instant.now().toString()
        val structuredError =
            StructuredErrorResponse(
                error =
                    ErrorDetails(
                        code = errorCode,
                        message = message,
                        type = errorType,
                        details = details,
                    ),
                context =
                    ErrorContext(
                        tool = toolName,
                        operation = operation,
                        parameters = parameters,
                        userMessage = userMessage,
                    ),
                timestamp = timestamp,
                requestId = requestId,
            )

        val jsonResponse =
            try {
                json.encodeToString(structuredError)
            } catch (e: Exception) {
                log.error(e) { "Failed to serialize structured error response" }
                // Fallback to simple error response
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

        log.error { "Structured error response: $jsonResponse" }

        return MCPToolResponse(
            content =
                listOf(
                    MCPContent(
                        type = "text",
                        text = jsonResponse,
                    ),
                ),
            isError = true,
        )
    }
}

/**
 * Extension function to create structured error responses
 */
fun createStructuredErrorResponse(
    toolName: String,
    operation: String,
): StructuredErrorResponseBuilder {
    return StructuredErrorResponseBuilder(toolName, operation)
}
