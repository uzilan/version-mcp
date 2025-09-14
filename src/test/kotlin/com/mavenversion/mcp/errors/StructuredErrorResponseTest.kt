package com.mavenversion.mcp.errors

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class StructuredErrorResponseTest {
    @Nested
    @DisplayName("Error Codes Tests")
    inner class ErrorCodesTests {
        @Test
        @DisplayName("Should have all required error codes defined")
        fun shouldHaveAllRequiredErrorCodesDefined() {
            // Verify parameter validation error codes
            assertThat(ErrorCodes.INVALID_PARAMETER).isEqualTo("INVALID_PARAMETER")
            assertThat(ErrorCodes.MISSING_PARAMETER).isEqualTo("MISSING_PARAMETER")
            assertThat(ErrorCodes.PARAMETER_TOO_SHORT).isEqualTo("PARAMETER_TOO_SHORT")
            assertThat(ErrorCodes.PARAMETER_TOO_LONG).isEqualTo("PARAMETER_TOO_LONG")
            assertThat(ErrorCodes.INVALID_PARAMETER_FORMAT).isEqualTo("INVALID_PARAMETER_FORMAT")

            // Verify network error codes
            assertThat(ErrorCodes.NETWORK_ERROR).isEqualTo("NETWORK_ERROR")
            assertThat(ErrorCodes.CONNECTION_TIMEOUT).isEqualTo("CONNECTION_TIMEOUT")
            assertThat(ErrorCodes.HOST_UNREACHABLE).isEqualTo("HOST_UNREACHABLE")
            assertThat(ErrorCodes.DNS_RESOLUTION_FAILED).isEqualTo("DNS_RESOLUTION_FAILED")

            // Verify MCP client error codes
            assertThat(ErrorCodes.MCP_CLIENT_ERROR).isEqualTo("MCP_CLIENT_ERROR")
            assertThat(ErrorCodes.MCP_CONNECTION_FAILED).isEqualTo("MCP_CONNECTION_FAILED")
            assertThat(ErrorCodes.MCP_TIMEOUT).isEqualTo("MCP_TIMEOUT")
            assertThat(ErrorCodes.MCP_PROTOCOL_ERROR).isEqualTo("MCP_PROTOCOL_ERROR")

            // Verify file system error codes
            assertThat(ErrorCodes.FILE_NOT_FOUND).isEqualTo("FILE_NOT_FOUND")
            assertThat(ErrorCodes.FILE_ACCESS_DENIED).isEqualTo("FILE_ACCESS_DENIED")
            assertThat(ErrorCodes.FILE_READ_ERROR).isEqualTo("FILE_READ_ERROR")
            assertThat(ErrorCodes.FILE_WRITE_ERROR).isEqualTo("FILE_WRITE_ERROR")
            assertThat(ErrorCodes.INVALID_FILE_FORMAT).isEqualTo("INVALID_FILE_FORMAT")

            // Verify dependency resolution error codes
            assertThat(ErrorCodes.DEPENDENCY_NOT_FOUND).isEqualTo("DEPENDENCY_NOT_FOUND")
            assertThat(ErrorCodes.VERSION_NOT_FOUND).isEqualTo("VERSION_NOT_FOUND")
            assertThat(ErrorCodes.INVALID_VERSION_FORMAT).isEqualTo("INVALID_VERSION_FORMAT")
            assertThat(ErrorCodes.DEPENDENCY_RESOLUTION_FAILED).isEqualTo("DEPENDENCY_RESOLUTION_FAILED")

            // Verify project detection error codes
            assertThat(ErrorCodes.PROJECT_NOT_DETECTED).isEqualTo("PROJECT_NOT_DETECTED")
            assertThat(ErrorCodes.UNSUPPORTED_PROJECT_TYPE).isEqualTo("UNSUPPORTED_PROJECT_TYPE")
            assertThat(ErrorCodes.BUILD_FILE_NOT_FOUND).isEqualTo("BUILD_FILE_NOT_FOUND")
            assertThat(ErrorCodes.BUILD_FILE_INVALID).isEqualTo("BUILD_FILE_INVALID")

            // Verify rate limiting error codes
            assertThat(ErrorCodes.RATE_LIMIT_EXCEEDED).isEqualTo("RATE_LIMIT_EXCEEDED")
            assertThat(ErrorCodes.QUOTA_EXCEEDED).isEqualTo("QUOTA_EXCEEDED")
            assertThat(ErrorCodes.SERVICE_UNAVAILABLE).isEqualTo("SERVICE_UNAVAILABLE")

            // Verify internal error codes
            assertThat(ErrorCodes.INTERNAL_ERROR).isEqualTo("INTERNAL_ERROR")
            assertThat(ErrorCodes.UNEXPECTED_ERROR).isEqualTo("UNEXPECTED_ERROR")
            assertThat(ErrorCodes.RESOURCE_EXHAUSTED).isEqualTo("RESOURCE_EXHAUSTED")
        }
    }

    @Nested
    @DisplayName("Error Types Tests")
    inner class ErrorTypesTests {
        @Test
        @DisplayName("Should have all required error types defined")
        fun shouldHaveAllRequiredErrorTypesDefined() {
            assertThat(ErrorTypes.VALIDATION_ERROR).isEqualTo("VALIDATION_ERROR")
            assertThat(ErrorTypes.NETWORK_ERROR).isEqualTo("NETWORK_ERROR")
            assertThat(ErrorTypes.FILE_SYSTEM_ERROR).isEqualTo("FILE_SYSTEM_ERROR")
            assertThat(ErrorTypes.DEPENDENCY_ERROR).isEqualTo("DEPENDENCY_ERROR")
            assertThat(ErrorTypes.PROJECT_ERROR).isEqualTo("PROJECT_ERROR")
            assertThat(ErrorTypes.RATE_LIMIT_ERROR).isEqualTo("RATE_LIMIT_ERROR")
            assertThat(ErrorTypes.INTERNAL_ERROR).isEqualTo("INTERNAL_ERROR")
        }
    }

    @Nested
    @DisplayName("StructuredErrorResponseBuilder Tests")
    inner class StructuredErrorResponseBuilderTests {
        @Test
        @DisplayName("Should create basic error response")
        fun shouldCreateBasicErrorResponse() {
            // When
            val response =
                createStructuredErrorResponse("test_tool", "test_operation")
                    .errorCode(ErrorCodes.INVALID_PARAMETER)
                    .errorType(ErrorTypes.VALIDATION_ERROR)
                    .message("Test error message")
                    .userMessage("Please check your input")
                    .build()

            // Then
            assertThat(response.isError).isTrue
            assertThat(response.content).hasSize(1)
            assertThat(response.content[0].type).isEqualTo("text")
            assertThat(response.content[0].text).contains("INVALID_PARAMETER")
            assertThat(response.content[0].text).contains("VALIDATION_ERROR")
            assertThat(response.content[0].text).contains("Test error message")
            assertThat(response.content[0].text).contains("Please check your input")
        }

        @Test
        @DisplayName("Should create error response with details")
        fun shouldCreateErrorResponseWithDetails() {
            // When
            val response =
                createStructuredErrorResponse("test_tool", "test_operation")
                    .errorCode(ErrorCodes.NETWORK_ERROR)
                    .errorType(ErrorTypes.NETWORK_ERROR)
                    .message("Connection failed")
                    .userMessage("Please check your internet connection")
                    .detail("host", "example.com")
                    .detail("port", "443")
                    .parameter("query", "test query")
                    .requestId("test-request-123")
                    .build()

            // Then
            assertThat(response.isError).isTrue
            assertThat(response.content).hasSize(1)
            assertThat(response.content[0].text).contains("NETWORK_ERROR")
            assertThat(response.content[0].text).contains("Connection failed")
            assertThat(response.content[0].text).contains("example.com")
            assertThat(response.content[0].text).contains("443")
            assertThat(response.content[0].text).contains("test query")
            assertThat(response.content[0].text).contains("test-request-123")
        }

        @Test
        @DisplayName("Should create error response with default values")
        fun shouldCreateErrorResponseWithDefaultValues() {
            // When
            val response =
                createStructuredErrorResponse("test_tool", "test_operation")
                    .build()

            // Then
            assertThat(response.isError).isTrue
            assertThat(response.content).hasSize(1)
            assertThat(response.content[0].text).contains("INTERNAL_ERROR")
            assertThat(response.content[0].text).contains("INTERNAL_ERROR")
            assertThat(response.content[0].text).contains("An unexpected error occurred")
            assertThat(response.content[0].text).contains("An error occurred while processing your request")
        }

        @Test
        @DisplayName("Should handle JSON serialization errors gracefully")
        fun shouldHandleJsonSerializationErrorsGracefully() {
            // When
            val response =
                createStructuredErrorResponse("test_tool", "test_operation")
                    .errorCode(ErrorCodes.INVALID_PARAMETER)
                    .errorType(ErrorTypes.VALIDATION_ERROR)
                    .message("Test error message")
                    .userMessage("Please check your input")
                    .build()

            // Then
            assertThat(response.isError).isTrue
            assertThat(response.content).hasSize(1)
            assertThat(response.content[0].type).isEqualTo("text")
            // Should contain valid JSON or fallback error message
            assertThat(response.content[0].text).isNotEmpty
        }
    }

    @Nested
    @DisplayName("Data Classes Tests")
    inner class DataClassesTests {
        @Test
        @DisplayName("Should create StructuredErrorResponse with all fields")
        fun shouldCreateStructuredErrorResponseWithAllFields() {
            // When
            val errorResponse =
                StructuredErrorResponse(
                    error =
                        ErrorDetails(
                            code = "TEST_ERROR",
                            message = "Test error message",
                            type = "TEST_TYPE",
                            details = mapOf("key" to "value"),
                        ),
                    context =
                        ErrorContext(
                            tool = "test_tool",
                            operation = "test_operation",
                            parameters = mapOf("param" to "value"),
                            userMessage = "User-friendly message",
                        ),
                    timestamp = "2024-01-01T00:00:00Z",
                    requestId = "test-request-123",
                )

            // Then
            assertThat(errorResponse.error.code).isEqualTo("TEST_ERROR")
            assertThat(errorResponse.error.message).isEqualTo("Test error message")
            assertThat(errorResponse.error.type).isEqualTo("TEST_TYPE")
            assertThat(errorResponse.error.details).containsEntry("key", "value")
            assertThat(errorResponse.context.tool).isEqualTo("test_tool")
            assertThat(errorResponse.context.operation).isEqualTo("test_operation")
            assertThat(errorResponse.context.parameters).containsEntry("param", "value")
            assertThat(errorResponse.context.userMessage).isEqualTo("User-friendly message")
            assertThat(errorResponse.timestamp).isEqualTo("2024-01-01T00:00:00Z")
            assertThat(errorResponse.requestId).isEqualTo("test-request-123")
        }

        @Test
        @DisplayName("Should create ErrorDetails with minimal fields")
        fun shouldCreateErrorDetailsWithMinimalFields() {
            // When
            val errorDetails =
                ErrorDetails(
                    code = "MINIMAL_ERROR",
                    message = "Minimal error message",
                    type = "MINIMAL_TYPE",
                )

            // Then
            assertThat(errorDetails.code).isEqualTo("MINIMAL_ERROR")
            assertThat(errorDetails.message).isEqualTo("Minimal error message")
            assertThat(errorDetails.type).isEqualTo("MINIMAL_TYPE")
            assertThat(errorDetails.details).isEmpty()
        }

        @Test
        @DisplayName("Should create ErrorContext with minimal fields")
        fun shouldCreateErrorContextWithMinimalFields() {
            // When
            val errorContext =
                ErrorContext(
                    tool = "minimal_tool",
                    operation = "minimal_operation",
                    userMessage = "Minimal user message",
                )

            // Then
            assertThat(errorContext.tool).isEqualTo("minimal_tool")
            assertThat(errorContext.operation).isEqualTo("minimal_operation")
            assertThat(errorContext.userMessage).isEqualTo("Minimal user message")
            assertThat(errorContext.parameters).isEmpty()
        }
    }
}
