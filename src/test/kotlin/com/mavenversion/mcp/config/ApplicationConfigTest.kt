package com.mavenversion.mcp.config

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class ApplicationConfigTest {
    @Nested
    @DisplayName("Default Configuration Tests")
    inner class DefaultConfigurationTests {
        @Test
        @DisplayName("Should create default configuration with correct values")
        fun shouldCreateDefaultConfigurationWithCorrectValues() {
            // When
            val config = ApplicationConfig()

            // Then
            assertThat(config.serverName).isEqualTo("maven-version-mcp-server")
            assertThat(config.serverVersion).isEqualTo("1.0.0")
            assertThat(config.protocolVersion).isEqualTo("2024-11-05")
            assertThat(config.logLevel).isEqualTo("INFO")
            assertThat(config.enableExamples).isFalse
            assertThat(config.mcpServerCommand).containsExactly("npx", "@modelcontextprotocol/server-playwright")
            assertThat(config.workingDirectory).isNull()
            assertThat(config.baseUrl).isEqualTo("https://mvnrepository.com")
            assertThat(config.maxRetries).isEqualTo(3)
            assertThat(config.retryDelayMs).isEqualTo(1000)
            assertThat(config.rateLimitDelayMs).isEqualTo(500)
        }
    }

    @Nested
    @DisplayName("Command Line Argument Parsing Tests")
    inner class CommandLineArgumentParsingTests {
        @Test
        @DisplayName("Should parse log level argument correctly")
        fun shouldParseLogLevelArgumentCorrectly() {
            // Given
            val args = arrayOf("--log-level", "DEBUG")

            // When
            val config = ApplicationConfig.fromArgs(args)

            // Then
            assertThat(config.logLevel).isEqualTo("DEBUG")
        }

        @Test
        @DisplayName("Should parse working directory argument correctly")
        fun shouldParseWorkingDirectoryArgumentCorrectly() {
            // Given
            val args = arrayOf("--working-dir", "/tmp/test")

            // When
            val config = ApplicationConfig.fromArgs(args)

            // Then
            assertThat(config.workingDirectory).isEqualTo("/tmp/test")
        }

        @Test
        @DisplayName("Should parse base URL argument correctly")
        fun shouldParseBaseUrlArgumentCorrectly() {
            // Given
            val args = arrayOf("--base-url", "https://test.mvnrepository.com")

            // When
            val config = ApplicationConfig.fromArgs(args)

            // Then
            assertThat(config.baseUrl).isEqualTo("https://test.mvnrepository.com")
        }

        @Test
        @DisplayName("Should parse max retries argument correctly")
        fun shouldParseMaxRetriesArgumentCorrectly() {
            // Given
            val args = arrayOf("--max-retries", "5")

            // When
            val config = ApplicationConfig.fromArgs(args)

            // Then
            assertThat(config.maxRetries).isEqualTo(5)
        }

        @Test
        @DisplayName("Should parse retry delay argument correctly")
        fun shouldParseRetryDelayArgumentCorrectly() {
            // Given
            val args = arrayOf("--retry-delay", "2000")

            // When
            val config = ApplicationConfig.fromArgs(args)

            // Then
            assertThat(config.retryDelayMs).isEqualTo(2000)
        }

        @Test
        @DisplayName("Should parse rate limit delay argument correctly")
        fun shouldParseRateLimitDelayArgumentCorrectly() {
            // Given
            val args = arrayOf("--rate-limit-delay", "1000")

            // When
            val config = ApplicationConfig.fromArgs(args)

            // Then
            assertThat(config.rateLimitDelayMs).isEqualTo(1000)
        }

        @Test
        @DisplayName("Should detect example mode from arguments")
        fun shouldDetectExampleModeFromArguments() {
            // Given
            val args = arrayOf("example")

            // When
            val config = ApplicationConfig.fromArgs(args)

            // Then
            assertThat(config.enableExamples).isTrue
        }

        @Test
        @DisplayName("Should parse multiple arguments correctly")
        fun shouldParseMultipleArgumentsCorrectly() {
            // Given
            val args =
                arrayOf(
                    "--log-level", "WARN",
                    "--base-url", "https://custom.mvnrepository.com",
                    "--max-retries", "7",
                    "--retry-delay", "3000",
                    "--rate-limit-delay", "1500",
                    "--working-dir", "/custom/dir",
                )

            // When
            val config = ApplicationConfig.fromArgs(args)

            // Then
            assertThat(config.logLevel).isEqualTo("WARN")
            assertThat(config.baseUrl).isEqualTo("https://custom.mvnrepository.com")
            assertThat(config.maxRetries).isEqualTo(7)
            assertThat(config.retryDelayMs).isEqualTo(3000)
            assertThat(config.rateLimitDelayMs).isEqualTo(1500)
            assertThat(config.workingDirectory).isEqualTo("/custom/dir")
        }
    }

    @Nested
    @DisplayName("Error Handling Tests")
    inner class ErrorHandlingTests {
        @Test
        @DisplayName("Should handle empty arguments array")
        fun shouldHandleEmptyArgumentsArray() {
            // Given
            val args = arrayOf<String>()

            // When
            val config = ApplicationConfig.fromArgs(args)

            // Then
            // Should use all default values
            assertThat(config.logLevel).isEqualTo("INFO")
            assertThat(config.baseUrl).isEqualTo("https://mvnrepository.com")
            assertThat(config.maxRetries).isEqualTo(3)
            assertThat(config.retryDelayMs).isEqualTo(1000)
            assertThat(config.rateLimitDelayMs).isEqualTo(500)
            assertThat(config.enableExamples).isFalse
        }

        @Test
        @DisplayName("Should handle invalid numeric arguments gracefully")
        fun shouldHandleInvalidNumericArgumentsGracefully() {
            // Given
            val args =
                arrayOf(
                    "--max-retries",
                    "invalid",
                    "--retry-delay",
                    "also-invalid",
                    "--rate-limit-delay",
                    "not-a-number",
                )

            // When
            val config = ApplicationConfig.fromArgs(args)

            // Then
            // Should fall back to default values
            assertThat(config.maxRetries).isEqualTo(3)
            assertThat(config.retryDelayMs).isEqualTo(1000)
            assertThat(config.rateLimitDelayMs).isEqualTo(500)
        }

        @Test
        @DisplayName("Should handle missing argument values gracefully")
        fun shouldHandleMissingArgumentValuesGracefully() {
            // Given
            val args = arrayOf("--log-level") // Missing value

            // When
            val config = ApplicationConfig.fromArgs(args)

            // Then
            // Should fall back to default value
            assertThat(config.logLevel).isEqualTo("INFO")
        }

        @Test
        @DisplayName("Should handle arguments at end of array gracefully")
        fun shouldHandleArgumentsAtEndOfArrayGracefully() {
            // Given
            val args = arrayOf("--log-level", "DEBUG", "--max-retries") // Last argument missing value

            // When
            val config = ApplicationConfig.fromArgs(args)

            // Then
            assertThat(config.logLevel).isEqualTo("DEBUG")
            assertThat(config.maxRetries).isEqualTo(3) // Should use default
        }

        @Test
        @DisplayName("Should handle unknown arguments gracefully")
        fun shouldHandleUnknownArgumentsGracefully() {
            // Given
            val args = arrayOf("--unknown-arg", "value", "--log-level", "WARN")

            // When
            val config = ApplicationConfig.fromArgs(args)

            // Then
            assertThat(config.logLevel).isEqualTo("WARN")
            // Other values should remain default
            assertThat(config.baseUrl).isEqualTo("https://mvnrepository.com")
        }
    }

    @Nested
    @DisplayName("Usage Information Tests")
    inner class UsageInformationTests {
        @Test
        @DisplayName("Should provide usage information")
        fun shouldProvideUsageInformation() {
            // When & Then
            // The method prints to stdout, so we just verify it doesn't throw
            // In a real test, we might capture stdout and verify content
            ApplicationConfig.printUsage() // This should not throw an exception
        }
    }
}
