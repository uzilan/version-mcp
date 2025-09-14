package com.mavenversion.mcp.logging

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import mu.KotlinLogging
import java.time.Instant
import java.util.UUID

private val log = KotlinLogging.logger {}
private val json = Json { prettyPrint = false }

/**
 * Structured logging service for comprehensive application logging
 */
class StructuredLoggingService {
    private val requestId = ThreadLocal<String>()

    /**
     * Set request ID for current thread
     */
    fun setRequestId(id: String) {
        requestId.set(id)
    }

    /**
     * Generate and set new request ID
     */
    fun generateRequestId(): String {
        val id = UUID.randomUUID().toString()
        setRequestId(id)
        return id
    }

    /**
     * Get current request ID
     */
    fun getCurrentRequestId(): String? = requestId.get()

    /**
     * Clear request ID for current thread
     */
    fun clearRequestId() {
        requestId.remove()
    }

    /**
     * Log tool execution start
     */
    fun logToolStart(
        toolName: String,
        operation: String,
        parameters: Map<String, String> = emptyMap(),
    ) {
        val logEntry =
            ToolExecutionLogEntry(
                event = "TOOL_START",
                toolName = toolName,
                operation = operation,
                parameters = parameters,
                requestId = getCurrentRequestId(),
                timestamp = Instant.now().toString(),
            )
        log.info { "Tool execution started: ${json.encodeToString(logEntry)}" }
    }

    /**
     * Log tool execution success
     */
    fun logToolSuccess(
        toolName: String,
        operation: String,
        duration: Long,
        resultSummary: String,
    ) {
        val logEntry =
            ToolExecutionLogEntry(
                event = "TOOL_SUCCESS",
                toolName = toolName,
                operation = operation,
                duration = duration,
                resultSummary = resultSummary,
                requestId = getCurrentRequestId(),
                timestamp = Instant.now().toString(),
            )
        log.info { "Tool execution completed: ${json.encodeToString(logEntry)}" }
    }

    /**
     * Log tool execution error
     */
    fun logToolError(
        toolName: String,
        operation: String,
        error: Throwable,
        duration: Long? = null,
    ) {
        val logEntry =
            ToolExecutionLogEntry(
                event = "TOOL_ERROR",
                toolName = toolName,
                operation = operation,
                error =
                    ErrorInfo(
                        type = error.javaClass.simpleName,
                        message = error.message ?: "Unknown error",
                        stackTrace = error.stackTrace.take(5).joinToString("\n") { it.toString() },
                    ),
                duration = duration,
                requestId = getCurrentRequestId(),
                timestamp = Instant.now().toString(),
            )
        log.error { "Tool execution failed: ${json.encodeToString(logEntry)}" }
    }

    /**
     * Log MCP client operations
     */
    fun logMCPOperation(
        operation: String,
        details: Map<String, String> = emptyMap(),
        success: Boolean = true,
        error: Throwable? = null,
    ) {
        val logEntry =
            MCPOperationLogEntry(
                event = if (success) "MCP_OPERATION_SUCCESS" else "MCP_OPERATION_ERROR",
                operation = operation,
                details = details,
                error =
                    error?.let { e ->
                        ErrorInfo(
                            type = e.javaClass.simpleName,
                            message = e.message ?: "Unknown error",
                            stackTrace = e.stackTrace.take(3).joinToString("\n") { it.toString() },
                        )
                    },
                requestId = getCurrentRequestId(),
                timestamp = Instant.now().toString(),
            )
        if (success) {
            log.debug { "MCP operation completed: ${json.encodeToString(logEntry)}" }
        } else {
            log.warn { "MCP operation failed: ${json.encodeToString(logEntry)}" }
        }
    }

    /**
     * Log file operations
     */
    fun logFileOperation(
        operation: String,
        filePath: String,
        success: Boolean = true,
        error: Throwable? = null,
        fileSize: Long? = null,
    ) {
        val logEntry =
            FileOperationLogEntry(
                event = if (success) "FILE_OPERATION_SUCCESS" else "FILE_OPERATION_ERROR",
                operation = operation,
                filePath = filePath,
                fileSize = fileSize,
                error =
                    error?.let { e ->
                        ErrorInfo(
                            type = e.javaClass.simpleName,
                            message = e.message ?: "Unknown error",
                            stackTrace = e.stackTrace.take(3).joinToString("\n") { it.toString() },
                        )
                    },
                requestId = getCurrentRequestId(),
                timestamp = Instant.now().toString(),
            )
        if (success) {
            log.debug { "File operation completed: ${json.encodeToString(logEntry)}" }
        } else {
            log.warn { "File operation failed: ${json.encodeToString(logEntry)}" }
        }
    }

    /**
     * Log network operations
     */
    fun logNetworkOperation(
        operation: String,
        url: String,
        success: Boolean = true,
        error: Throwable? = null,
        responseTime: Long? = null,
        statusCode: Int? = null,
    ) {
        val logEntry =
            NetworkOperationLogEntry(
                event = if (success) "NETWORK_OPERATION_SUCCESS" else "NETWORK_OPERATION_ERROR",
                operation = operation,
                url = url,
                responseTime = responseTime,
                statusCode = statusCode,
                error =
                    error?.let { e ->
                        ErrorInfo(
                            type = e.javaClass.simpleName,
                            message = e.message ?: "Unknown error",
                            stackTrace = e.stackTrace.take(3).joinToString("\n") { it.toString() },
                        )
                    },
                requestId = getCurrentRequestId(),
                timestamp = Instant.now().toString(),
            )
        if (success) {
            log.debug { "Network operation completed: ${json.encodeToString(logEntry)}" }
        } else {
            log.warn { "Network operation failed: ${json.encodeToString(logEntry)}" }
        }
    }

    /**
     * Log application lifecycle events
     */
    fun logApplicationEvent(
        event: String,
        details: Map<String, String> = emptyMap(),
    ) {
        val logEntry =
            ApplicationEventLogEntry(
                event = event,
                details = details,
                requestId = getCurrentRequestId(),
                timestamp = Instant.now().toString(),
            )
        log.info { "Application event: ${json.encodeToString(logEntry)}" }
    }
}

/**
 * Log entry data classes
 */
@Serializable
data class ToolExecutionLogEntry(
    val event: String,
    val toolName: String,
    val operation: String,
    val parameters: Map<String, String> = emptyMap(),
    val duration: Long? = null,
    val resultSummary: String? = null,
    val error: ErrorInfo? = null,
    val requestId: String? = null,
    val timestamp: String,
)

@Serializable
data class MCPOperationLogEntry(
    val event: String,
    val operation: String,
    val details: Map<String, String> = emptyMap(),
    val error: ErrorInfo? = null,
    val requestId: String? = null,
    val timestamp: String,
)

@Serializable
data class FileOperationLogEntry(
    val event: String,
    val operation: String,
    val filePath: String,
    val fileSize: Long? = null,
    val error: ErrorInfo? = null,
    val requestId: String? = null,
    val timestamp: String,
)

@Serializable
data class NetworkOperationLogEntry(
    val event: String,
    val operation: String,
    val url: String,
    val responseTime: Long? = null,
    val statusCode: Int? = null,
    val error: ErrorInfo? = null,
    val requestId: String? = null,
    val timestamp: String,
)

@Serializable
data class ApplicationEventLogEntry(
    val event: String,
    val details: Map<String, String> = emptyMap(),
    val requestId: String? = null,
    val timestamp: String,
)

@Serializable
data class ErrorInfo(
    val type: String,
    val message: String,
    val stackTrace: String,
)
