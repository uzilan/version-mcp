package com.mavenversion.mcp.integration

import com.mavenversion.mcp.config.ApplicationConfig
import com.mavenversion.mcp.service.ApplicationService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.util.concurrent.TimeUnit

@Timeout(value = 30, unit = TimeUnit.SECONDS)
class ApplicationIntegrationTest {
    private lateinit var applicationService: ApplicationService
    private lateinit var config: ApplicationConfig

    @BeforeEach
    fun setUp() {
        config =
            ApplicationConfig(
                enableExamples = false,
                // Reduce log noise during tests
                logLevel = "WARN",
            )
        applicationService = ApplicationService(config)
    }

    @AfterEach
    fun tearDown() {
        if (::applicationService.isInitialized) {
            applicationService.shutdown(0)
        }
    }

    @Nested
    @DisplayName("Application Lifecycle Tests")
    inner class ApplicationLifecycleTests {
        @Test
        @DisplayName("Should create application service with valid configuration")
        fun shouldCreateApplicationServiceWithValidConfiguration() {
            // When
            val status = applicationService.getStatus()

            // Then
            assertThat(status.config).isEqualTo(config)
            assertThat(status.isRunning).isFalse
            assertThat(status.isShuttingDown).isFalse
            assertThat(status.serverRunning).isFalse
        }

        @Test
        @DisplayName("Should handle shutdown gracefully when not started")
        fun shouldHandleShutdownGracefullyWhenNotStarted() {
            // When
            applicationService.shutdown()

            // Then
            val status = applicationService.getStatus()
            assertThat(status.isShuttingDown).isTrue
        }
    }

    @Nested
    @DisplayName("Configuration Tests")
    inner class ConfigurationTests {
        @Test
        @DisplayName("Should parse command line arguments correctly")
        fun shouldParseCommandLineArgumentsCorrectly() {
            // Given
            val args =
                arrayOf(
                    "--log-level", "DEBUG",
                    "--base-url", "https://test.mvnrepository.com",
                    "--max-retries", "5",
                    "--retry-delay", "2000",
                    "--rate-limit-delay", "1000",
                )

            // When
            val parsedConfig = ApplicationConfig.fromArgs(args)

            // Then
            assertThat(parsedConfig.logLevel).isEqualTo("DEBUG")
            assertThat(parsedConfig.baseUrl).isEqualTo("https://test.mvnrepository.com")
            assertThat(parsedConfig.maxRetries).isEqualTo(5)
            assertThat(parsedConfig.retryDelayMs).isEqualTo(2000)
            assertThat(parsedConfig.rateLimitDelayMs).isEqualTo(1000)
        }

        @Test
        @DisplayName("Should use default values for missing arguments")
        fun shouldUseDefaultValuesForMissingArguments() {
            // Given
            val args = arrayOf<String>()

            // When
            val parsedConfig = ApplicationConfig.fromArgs(args)

            // Then
            assertThat(parsedConfig.logLevel).isEqualTo("INFO")
            assertThat(parsedConfig.baseUrl).isEqualTo("https://mvnrepository.com")
            assertThat(parsedConfig.maxRetries).isEqualTo(3)
            assertThat(parsedConfig.retryDelayMs).isEqualTo(1000)
            assertThat(parsedConfig.rateLimitDelayMs).isEqualTo(500)
        }

        @Test
        @DisplayName("Should detect example mode from arguments")
        fun shouldDetectExampleModeFromArguments() {
            // Given
            val args = arrayOf("example")

            // When
            val parsedConfig = ApplicationConfig.fromArgs(args)

            // Then
            assertThat(parsedConfig.enableExamples).isTrue
        }
    }

    @Nested
    @DisplayName("Error Handling Tests")
    inner class ErrorHandlingTests {
        @Test
        @DisplayName("Should handle invalid numeric arguments gracefully")
        fun shouldHandleInvalidNumericArgumentsGracefully() {
            // Given
            val args = arrayOf("--max-retries", "invalid", "--retry-delay", "also-invalid")

            // When
            val parsedConfig = ApplicationConfig.fromArgs(args)

            // Then
            // Should fall back to default values
            assertThat(parsedConfig.maxRetries).isEqualTo(3)
            assertThat(parsedConfig.retryDelayMs).isEqualTo(1000)
        }

        @Test
        @DisplayName("Should handle missing argument values gracefully")
        fun shouldHandleMissingArgumentValuesGracefully() {
            // Given
            val args = arrayOf("--log-level") // Missing value

            // When
            val parsedConfig = ApplicationConfig.fromArgs(args)

            // Then
            // Should fall back to default value
            assertThat(parsedConfig.logLevel).isEqualTo("INFO")
        }
    }
}
