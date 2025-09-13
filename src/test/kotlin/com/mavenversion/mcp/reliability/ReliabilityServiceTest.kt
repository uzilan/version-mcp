package com.mavenversion.mcp.reliability

import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import kotlin.system.measureTimeMillis

@DisplayName("ReliabilityService Tests")
class ReliabilityServiceTest {
    @Nested
    @DisplayName("Retry Logic Tests")
    inner class RetryLogicTests {
        @Test
        @DisplayName("Should succeed on first attempt when operation succeeds")
        fun shouldSucceedOnFirstAttempt() =
            runTest {
                val service = ReliabilityService(maxRetries = 3, baseDelayMs = 100, rateLimitDelayMs = 0)
                var attempts = 0

                val result =
                    service.executeWithRetry("test operation") {
                        attempts++
                        "success"
                    }

                assertThat(result.isSuccess).isTrue()
                assertThat(result.getOrNull()).isEqualTo("success")
                assertThat(attempts).isEqualTo(1)
            }

        @Test
        @DisplayName("Should retry on retryable exceptions")
        fun shouldRetryOnRetryableExceptions() =
            runTest {
                val service = ReliabilityService(maxRetries = 3, baseDelayMs = 50, rateLimitDelayMs = 0)
                var attempts = 0

                val result =
                    service.executeWithRetry(
                        operation = "test operation",
                        retryableExceptions = setOf(RuntimeException::class.java),
                    ) {
                        attempts++
                        if (attempts < 3) {
                            throw RuntimeException("Temporary failure")
                        }
                        "success after retries"
                    }

                assertThat(result.isSuccess).isTrue()
                assertThat(result.getOrNull()).isEqualTo("success after retries")
                assertThat(attempts).isEqualTo(3)
            }

        @Test
        @DisplayName("Should not retry on non-retryable exceptions")
        fun shouldNotRetryOnNonRetryableExceptions() =
            runTest {
                val service = ReliabilityService(maxRetries = 3, baseDelayMs = 50, rateLimitDelayMs = 0)
                var attempts = 0

                val result =
                    service.executeWithRetry(
                        operation = "test operation",
                        retryableExceptions = setOf(RuntimeException::class.java),
                    ) {
                        attempts++
                        throw IllegalArgumentException("Non-retryable failure")
                    }

                assertThat(result.isFailure).isTrue()
                assertThat(attempts).isEqualTo(1)
                assertThat(result.exceptionOrNull()).isInstanceOf(IllegalArgumentException::class.java)
            }

        @Test
        @DisplayName("Should fail after max retries")
        fun shouldFailAfterMaxRetries() =
            runTest {
                val service = ReliabilityService(maxRetries = 2, baseDelayMs = 50, rateLimitDelayMs = 0)
                var attempts = 0

                val result =
                    service.executeWithRetry(
                        operation = "test operation",
                        retryableExceptions = setOf(RuntimeException::class.java),
                    ) {
                        attempts++
                        throw RuntimeException("Persistent failure")
                    }

                assertThat(result.isFailure).isTrue()
                assertThat(attempts).isEqualTo(2)
                assertThat(result.exceptionOrNull()).isInstanceOf(RuntimeException::class.java)
            }

        @Test
        @DisplayName("Should apply exponential backoff")
        fun shouldApplyExponentialBackoff() =
            runTest {
                val service = ReliabilityService(maxRetries = 3, baseDelayMs = 100, rateLimitDelayMs = 0)
                var attempts = 0

                val timeMs =
                    measureTimeMillis {
                        service.executeWithRetry(
                            operation = "test operation",
                            retryableExceptions = setOf(RuntimeException::class.java),
                        ) {
                            attempts++
                            if (attempts < 3) {
                                throw RuntimeException("Temporary failure")
                            }
                            "success"
                        }
                    }

                // Should have delays: ~100ms + ~200ms = ~300ms minimum
                assertThat(timeMs).isGreaterThan(250)
                assertThat(attempts).isEqualTo(3)
            }
    }

    @Nested
    @DisplayName("Rate Limiting Tests")
    inner class RateLimitingTests {
        @Test
        @DisplayName("Should apply rate limiting between requests")
        fun shouldApplyRateLimitingBetweenRequests() =
            runTest {
                val service = ReliabilityService(rateLimitDelayMs = 50)

                val timeMs =
                    measureTimeMillis {
                        service.executeWithRetry("first request") { "result1" }
                        service.executeWithRetry("second request") { "result2" }
                    }

                // Should have at least one rate limit delay (50ms)
                assertThat(timeMs).isGreaterThan(30)
            }

        @Test
        @DisplayName("Should skip rate limiting when disabled")
        fun shouldSkipRateLimitingWhenDisabled() =
            runTest {
                val service = ReliabilityService(rateLimitDelayMs = 0)

                val timeMs =
                    measureTimeMillis {
                        service.executeWithRetry("first request") { "result1" }
                        service.executeWithRetry("second request") { "result2" }
                    }

                // Should complete quickly without rate limiting
                assertThat(timeMs).isLessThan(50)
            }
    }

    @Nested
    @DisplayName("Exception Classification Tests")
    inner class ExceptionClassificationTests {
        @Test
        @DisplayName("Should identify retryable network exceptions")
        fun shouldIdentifyRetryableNetworkExceptions() {
            val service = ReliabilityService()

            assertThat(service.isRetryableException(Exception("Connection timeout"))).isTrue()
            assertThat(service.isRetryableException(Exception("Network error"))).isTrue()
            assertThat(service.isRetryableException(Exception("Socket closed"))).isTrue()
            assertThat(service.isRetryableException(Exception("502 Bad Gateway"))).isTrue()
            assertThat(service.isRetryableException(Exception("503 Service Unavailable"))).isTrue()
        }

        @Test
        @DisplayName("Should identify retryable Playwright exceptions")
        fun shouldIdentifyRetryablePlaywrightExceptions() {
            val service = ReliabilityService()

            assertThat(service.isRetryableException(Exception("Page crashed"))).isTrue()
            assertThat(service.isRetryableException(Exception("Browser disconnected"))).isTrue()
            assertThat(service.isRetryableException(Exception("Navigation timeout"))).isTrue()
            assertThat(service.isRetryableException(Exception("Element not found"))).isTrue()
        }

        @Test
        @DisplayName("Should identify non-retryable exceptions")
        fun shouldIdentifyNonRetryableExceptions() {
            val service = ReliabilityService()

            assertThat(service.isRetryableException(Exception("Invalid argument"))).isFalse()
            assertThat(service.isRetryableException(Exception("Authentication failed"))).isFalse()
            assertThat(service.isRetryableException(Exception("Permission denied"))).isFalse()
        }
    }

    @Nested
    @DisplayName("Website Structure Error Handling Tests")
    inner class WebsiteStructureErrorHandlingTests {
        @Test
        @DisplayName("Should handle structure change errors gracefully")
        fun shouldHandleStructureChangeErrorsGracefully() {
            val service = ReliabilityService()
            val originalException = PlaywrightMCPException("Element not found")

            val result =
                service.handleStructureChangeError(
                    "test operation",
                    ".test-selector",
                    originalException,
                )

            assertThat(result.isFailure).isTrue()
            assertThat(result.exceptionOrNull()).isInstanceOf(WebsiteStructureException::class.java)

            val exception = result.exceptionOrNull() as WebsiteStructureException
            assertThat(exception.message).contains("Website structure may have changed")
            assertThat(exception.message).contains(".test-selector")
            assertThat(exception.cause).isEqualTo(originalException)
        }
    }
}
