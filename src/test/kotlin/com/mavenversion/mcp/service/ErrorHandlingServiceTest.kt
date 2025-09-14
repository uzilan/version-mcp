package com.mavenversion.mcp.service

import com.mavenversion.mcp.reliability.MCPToolException
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.TimeoutException

class ErrorHandlingServiceTest {
    private lateinit var errorHandlingService: ErrorHandlingService

    @BeforeEach
    fun setUp() {
        errorHandlingService = ErrorHandlingService()
    }

    @Nested
    @DisplayName("Exception Handling Tests")
    inner class ExceptionHandlingTests {
        @Test
        @DisplayName("Should handle MCPToolException correctly")
        fun shouldHandleMCPToolExceptionCorrectly() {
            // Given
            val exception = MCPToolException("MCP tool failed")

            // When
            val response = errorHandlingService.handleException(exception, "test context")

            // Then
            assertThat(response.isError).isTrue
            assertThat(response.content).hasSize(1)
            assertThat(response.content[0].type).isEqualTo("text")
            assertThat(response.content[0].text).contains("MCP Tool Error: MCP tool failed")
        }

        @Test
        @DisplayName("Should handle IllegalArgumentException correctly")
        fun shouldHandleIllegalArgumentExceptionCorrectly() {
            // Given
            val exception = IllegalArgumentException("Invalid parameter")

            // When
            val response = errorHandlingService.handleException(exception, "validation")

            // Then
            assertThat(response.isError).isTrue
            assertThat(response.content).hasSize(1)
            assertThat(response.content[0].type).isEqualTo("text")
            assertThat(response.content[0].text).contains("Invalid input: Invalid parameter")
        }

        @Test
        @DisplayName("Should handle SecurityException correctly")
        fun shouldHandleSecurityExceptionCorrectly() {
            // Given
            val exception = SecurityException("Access denied")

            // When
            val response = errorHandlingService.handleException(exception, "file access")

            // Then
            assertThat(response.isError).isTrue
            assertThat(response.content).hasSize(1)
            assertThat(response.content[0].type).isEqualTo("text")
            assertThat(response.content[0].text).contains("Access denied: Access denied")
        }

        @Test
        @DisplayName("Should handle IOException correctly")
        fun shouldHandleIOExceptionCorrectly() {
            // Given
            val exception = IOException("File not found")

            // When
            val response = errorHandlingService.handleException(exception, "file operation")

            // Then
            assertThat(response.isError).isTrue
            assertThat(response.content).hasSize(1)
            assertThat(response.content[0].type).isEqualTo("text")
            assertThat(response.content[0].text).contains("I/O error: File not found")
        }

        @Test
        @DisplayName("Should handle UnknownHostException correctly")
        fun shouldHandleUnknownHostExceptionCorrectly() {
            // Given
            val exception = UnknownHostException("example.com")

            // When
            val response = errorHandlingService.handleException(exception, "network")

            // Then
            assertThat(response.isError).isTrue
            assertThat(response.content).hasSize(1)
            assertThat(response.content[0].type).isEqualTo("text")
            assertThat(response.content[0].text).contains("Network error: Unable to connect to host")
        }

        @Test
        @DisplayName("Should handle SocketTimeoutException correctly")
        fun shouldHandleSocketTimeoutExceptionCorrectly() {
            // Given
            val exception = SocketTimeoutException("Read timed out")

            // When
            val response = errorHandlingService.handleException(exception, "network")

            // Then
            assertThat(response.isError).isTrue
            assertThat(response.content).hasSize(1)
            assertThat(response.content[0].type).isEqualTo("text")
            assertThat(response.content[0].text).contains("Network timeout: Request took too long")
        }

        @Test
        @DisplayName("Should handle TimeoutException correctly")
        fun shouldHandleTimeoutExceptionCorrectly() {
            // Given
            val exception = TimeoutException("Operation timed out")

            // When
            val response = errorHandlingService.handleException(exception, "operation")

            // Then
            assertThat(response.isError).isTrue
            assertThat(response.content).hasSize(1)
            assertThat(response.content[0].type).isEqualTo("text")
            assertThat(response.content[0].text).contains("Operation timeout: Operation timed out")
        }

        @Test
        @DisplayName("Should handle generic Exception correctly")
        fun shouldHandleGenericExceptionCorrectly() {
            // Given
            val exception = RuntimeException("Unexpected error")

            // When
            val response = errorHandlingService.handleException(exception, "generic")

            // Then
            assertThat(response.isError).isTrue
            assertThat(response.content).hasSize(1)
            assertThat(response.content[0].type).isEqualTo("text")
            assertThat(response.content[0].text).contains("Unexpected error: Unexpected error")
        }

        @Test
        @DisplayName("Should handle Exception with null message correctly")
        fun shouldHandleExceptionWithNullMessageCorrectly() {
            // Given
            val exception = RuntimeException(null as String?)

            // When
            val response = errorHandlingService.handleException(exception, "null message")

            // Then
            assertThat(response.isError).isTrue
            assertThat(response.content).hasSize(1)
            assertThat(response.content[0].type).isEqualTo("text")
            assertThat(response.content[0].text).contains("Unexpected error: Unknown error occurred")
        }
    }

    @Nested
    @DisplayName("Specific Error Handling Tests")
    inner class SpecificErrorHandlingTests {
        @Test
        @DisplayName("Should handle validation errors correctly")
        fun shouldHandleValidationErrorsCorrectly() {
            // When
            val response = errorHandlingService.handleValidationError("Required field is missing")

            // Then
            assertThat(response.isError).isTrue
            assertThat(response.content).hasSize(1)
            assertThat(response.content[0].type).isEqualTo("text")
            assertThat(response.content[0].text).contains("Validation error: Required field is missing")
        }

        @Test
        @DisplayName("Should handle not found errors correctly")
        fun shouldHandleNotFoundErrorsCorrectly() {
            // When
            val response = errorHandlingService.handleNotFoundError("dependency.json")

            // Then
            assertThat(response.isError).isTrue
            assertThat(response.content).hasSize(1)
            assertThat(response.content[0].type).isEqualTo("text")
            assertThat(response.content[0].text).contains("Resource not found: dependency.json")
        }

        @Test
        @DisplayName("Should handle permission errors correctly")
        fun shouldHandlePermissionErrorsCorrectly() {
            // When
            val response = errorHandlingService.handlePermissionError("/etc/passwd")

            // Then
            assertThat(response.isError).isTrue
            assertThat(response.content).hasSize(1)
            assertThat(response.content[0].type).isEqualTo("text")
            assertThat(response.content[0].text).contains("Permission denied: Cannot access /etc/passwd")
        }

        @Test
        @DisplayName("Should handle network errors correctly")
        fun shouldHandleNetworkErrorsCorrectly() {
            // When
            val response = errorHandlingService.handleNetworkError("https://example.com")

            // Then
            assertThat(response.isError).isTrue
            assertThat(response.content).hasSize(1)
            assertThat(response.content[0].type).isEqualTo("text")
            assertThat(response.content[0].text).contains("Network error: Unable to access https://example.com")
        }

        @Test
        @DisplayName("Should handle rate limit errors correctly")
        fun shouldHandleRateLimitErrorsCorrectly() {
            // When
            val response = errorHandlingService.handleRateLimitError("mvnrepository.com", 5000)

            // Then
            assertThat(response.isError).isTrue
            assertThat(response.content).hasSize(1)
            assertThat(response.content[0].type).isEqualTo("text")
            assertThat(response.content[0].text).contains("Rate limit exceeded for mvnrepository.com")
            assertThat(response.content[0].text).contains("Please retry after 5000ms")
        }

        @Test
        @DisplayName("Should handle rate limit errors without retry time correctly")
        fun shouldHandleRateLimitErrorsWithoutRetryTimeCorrectly() {
            // When
            val response = errorHandlingService.handleRateLimitError("mvnrepository.com")

            // Then
            assertThat(response.isError).isTrue
            assertThat(response.content).hasSize(1)
            assertThat(response.content[0].type).isEqualTo("text")
            assertThat(response.content[0].text).contains("Rate limit exceeded for mvnrepository.com")
            assertThat(response.content[0].text).contains("Please retry later")
        }

        @Test
        @DisplayName("Should handle parsing errors correctly")
        fun shouldHandleParsingErrorsCorrectly() {
            // When
            val response = errorHandlingService.handleParsingError("invalid json", "JSON")

            // Then
            assertThat(response.isError).isTrue
            assertThat(response.content).hasSize(1)
            assertThat(response.content[0].type).isEqualTo("text")
            assertThat(response.content[0].text).contains("Parsing error: Unable to parse content as JSON")
        }
    }

    @Nested
    @DisplayName("Success Response Tests")
    inner class SuccessResponseTests {
        @Test
        @DisplayName("Should create success response with text content")
        fun shouldCreateSuccessResponseWithTextContent() {
            // When
            val response = errorHandlingService.createSuccessResponse("Operation completed successfully")

            // Then
            assertThat(response.isError).isFalse
            assertThat(response.content).hasSize(1)
            assertThat(response.content[0].type).isEqualTo("text")
            assertThat(response.content[0].text).isEqualTo("Operation completed successfully")
        }

        @Test
        @DisplayName("Should create success response with custom type")
        fun shouldCreateSuccessResponseWithCustomType() {
            // When
            val response = errorHandlingService.createSuccessResponse("Data", "json")

            // Then
            assertThat(response.isError).isFalse
            assertThat(response.content).hasSize(1)
            assertThat(response.content[0].type).isEqualTo("json")
            assertThat(response.content[0].text).isEqualTo("Data")
        }

        @Test
        @DisplayName("Should create success response with structured data")
        fun shouldCreateSuccessResponseWithStructuredData() {
            // When
            val response = errorHandlingService.createSuccessResponseWithData("{\"result\": \"success\"}", "application/json")

            // Then
            assertThat(response.isError).isFalse
            assertThat(response.content).hasSize(1)
            assertThat(response.content[0].type).isEqualTo("data")
            assertThat(response.content[0].data).isEqualTo("{\"result\": \"success\"}")
            assertThat(response.content[0].mimeType).isEqualTo("application/json")
        }
    }
}
