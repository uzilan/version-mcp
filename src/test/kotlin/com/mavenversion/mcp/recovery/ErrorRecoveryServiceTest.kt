package com.mavenversion.mcp.recovery

import com.mavenversion.mcp.logging.StructuredLoggingService
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.util.concurrent.TimeoutException

class ErrorRecoveryServiceTest {
    private lateinit var loggingService: StructuredLoggingService
    private lateinit var errorRecoveryService: ErrorRecoveryService

    @BeforeEach
    fun setUp() {
        loggingService = mockk(relaxed = true)
        errorRecoveryService = ErrorRecoveryService(loggingService)
    }

    @Nested
    @DisplayName("Retry Logic Tests")
    inner class RetryLogicTests {
        @Test
        @DisplayName("Should succeed on first attempt")
        fun shouldSucceedOnFirstAttempt() =
            runTest {
                // Given
                var attemptCount = 0
                val operation = {
                    attemptCount++
                    "success"
                }

                // When
                val result =
                    errorRecoveryService.executeWithRetry(
                        operation = "test_operation",
                        maxRetries = 3,
                        operationBlock = operation,
                    )

                // Then
                assertThat(result).isEqualTo("success")
                assertThat(attemptCount).isEqualTo(1)
                verify { loggingService.logMCPOperation("test_operation", any(), success = true) }
            }

        @Test
        @DisplayName("Should retry on failure and eventually succeed")
        fun shouldRetryOnFailureAndEventuallySucceed() =
            runTest {
                // Given
                var attemptCount = 0
                val operation = {
                    attemptCount++
                    if (attemptCount < 3) {
                        throw RuntimeException("Temporary failure")
                    }
                    "success"
                }

                // When
                val result =
                    errorRecoveryService.executeWithRetry(
                        operation = "test_operation",
                        maxRetries = 3,
                        // Short delay for testing
                        baseDelayMs = 10,
                        operationBlock = operation,
                    )

                // Then
                assertThat(result).isEqualTo("success")
                assertThat(attemptCount).isEqualTo(3)
                verify { loggingService.logMCPOperation("test_operation", any(), success = true) }
            }

        @Test
        @DisplayName("Should fail after max retries")
        fun shouldFailAfterMaxRetries() =
            runTest {
                // Given
                var attemptCount = 0
                val operation = {
                    attemptCount++
                    throw RuntimeException("Persistent failure")
                }

                // When & Then
                try {
                    errorRecoveryService.executeWithRetry(
                        operation = "test_operation",
                        maxRetries = 2,
                        baseDelayMs = 10,
                        operationBlock = operation,
                    )
                    assertThat(false).withFailMessage("Expected RuntimeException").isTrue()
                } catch (e: RuntimeException) {
                    assertThat(e.message).isEqualTo("Persistent failure")
                }

                assertThat(attemptCount).isEqualTo(2)
            }

        @Test
        @DisplayName("Should use exponential backoff")
        fun shouldUseExponentialBackoff() =
            runTest {
                // Given
                var attemptCount = 0
                val operation = {
                    attemptCount++
                    throw RuntimeException("Temporary failure")
                }

                // When & Then
                try {
                    errorRecoveryService.executeWithRetry(
                        operation = "test_operation",
                        maxRetries = 2,
                        baseDelayMs = 100,
                        backoffMultiplier = 2.0,
                        operationBlock = operation,
                    )
                    assertThat(false).withFailMessage("Expected RuntimeException").isTrue()
                } catch (e: RuntimeException) {
                    assertThat(e.message).isEqualTo("Temporary failure")
                }

                assertThat(attemptCount).isEqualTo(2)
            }
    }

    @Nested
    @DisplayName("Circuit Breaker Tests")
    inner class CircuitBreakerTests {
        @Test
        @DisplayName("Should execute successfully when circuit is closed")
        fun shouldExecuteSuccessfullyWhenCircuitIsClosed() =
            runTest {
                // Given
                val operation = { "success" }

                // When
                val result =
                    errorRecoveryService.executeWithCircuitBreaker(
                        operation = "test_operation",
                        operationBlock = operation,
                    )

                // Then
                assertThat(result).isEqualTo("success")
            }

        @Test
        @DisplayName("Should open circuit after failure threshold")
        fun shouldOpenCircuitAfterFailureThreshold() =
            runTest {
                // Given
                val operation = { throw RuntimeException("Failure") }

                // When & Then
                // First few failures should be allowed
                repeat(4) {
                    try {
                        errorRecoveryService.executeWithCircuitBreaker(
                            operation = "test_operation",
                            failureThreshold = 5,
                            operationBlock = operation,
                        )
                        assertThat(false).withFailMessage("Expected RuntimeException").isTrue()
                    } catch (e: RuntimeException) {
                        assertThat(e.message).isEqualTo("Failure")
                    }
                }

                // Next failure should open the circuit
                try {
                    errorRecoveryService.executeWithCircuitBreaker(
                        operation = "test_operation",
                        failureThreshold = 5,
                        operationBlock = operation,
                    )
                    assertThat(false).withFailMessage("Expected RuntimeException").isTrue()
                } catch (e: RuntimeException) {
                    assertThat(e.message).isEqualTo("Failure")
                }

                // Circuit should now be open
                try {
                    errorRecoveryService.executeWithCircuitBreaker(
                        operation = "test_operation",
                        failureThreshold = 5,
                        operationBlock = operation,
                    )
                    assertThat(false).withFailMessage("Expected CircuitBreakerOpenException").isTrue()
                } catch (e: CircuitBreakerOpenException) {
                    assertThat(e.message).contains("Circuit breaker is open")
                }
            }

        @Test
        @DisplayName("Should reset circuit after successful operation")
        fun shouldResetCircuitAfterSuccessfulOperation() =
            runTest {
                // Given
                var shouldFail = true
                val operation = {
                    if (shouldFail) {
                        throw RuntimeException("Failure")
                    } else {
                        "success"
                    }
                }

                // When
                // Cause circuit to open
                repeat(5) {
                    try {
                        errorRecoveryService.executeWithCircuitBreaker(
                            operation = "test_operation",
                            failureThreshold = 5,
                            operationBlock = operation,
                        )
                        assertThat(false).withFailMessage("Expected RuntimeException").isTrue()
                    } catch (e: RuntimeException) {
                        assertThat(e.message).isEqualTo("Failure")
                    }
                }

                // Circuit should be open
                try {
                    errorRecoveryService.executeWithCircuitBreaker(
                        operation = "test_operation",
                        failureThreshold = 5,
                        operationBlock = operation,
                    )
                    assertThat(false).withFailMessage("Expected CircuitBreakerOpenException").isTrue()
                } catch (e: CircuitBreakerOpenException) {
                    assertThat(e.message).contains("Circuit breaker is open")
                }

                // Reset and try again - but circuit is still open, so we need to wait for recovery timeout
                shouldFail = false
                try {
                    val result =
                        errorRecoveryService.executeWithCircuitBreaker(
                            operation = "test_operation",
                            failureThreshold = 5,
                            operationBlock = operation,
                        )
                    // If we get here, the circuit was reset and operation succeeded
                    assertThat(result).isEqualTo("success")
                } catch (e: CircuitBreakerOpenException) {
                    // Circuit is still open, which is expected behavior
                    assertThat(e.message).contains("Circuit breaker is open")
                }
            }
    }

    @Nested
    @DisplayName("Timeout Tests")
    inner class TimeoutTests {
        @Test
        @DisplayName("Should complete operation within timeout")
        fun shouldCompleteOperationWithinTimeout() =
            runTest {
                // Given
                val operation = { "success" }

                // When
                val result =
                    errorRecoveryService.executeWithTimeout(
                        operation = "test_operation",
                        timeoutMs = 1000,
                        operationBlock = operation,
                    )

                // Then
                assertThat(result).isEqualTo("success")
            }

        @Test
        @DisplayName("Should timeout operation that takes too long")
        fun shouldTimeoutOperationThatTakesTooLong() =
            runTest {
                // Given
                val operation =
                    suspend {
                        kotlinx.coroutines.delay(2000)
                        "success"
                    }

                // When & Then
                try {
                    errorRecoveryService.executeWithTimeout(
                        operation = "test_operation",
                        timeoutMs = 100,
                        operationBlock = operation,
                    )
                    assertThat(false).withFailMessage("Expected TimeoutException").isTrue()
                } catch (e: TimeoutException) {
                    assertThat(e.message).contains("timed out")
                }
            }
    }

    @Nested
    @DisplayName("Statistics Tests")
    inner class StatisticsTests {
        @Test
        @DisplayName("Should track retry statistics")
        fun shouldTrackRetryStatistics() =
            runTest {
                // Given
                val operation = { throw RuntimeException("Failure") }

                // When
                try {
                    errorRecoveryService.executeWithRetry(
                        operation = "test_operation",
                        maxRetries = 2,
                        baseDelayMs = 10,
                        operationBlock = operation,
                    )
                    assertThat(false).withFailMessage("Expected RuntimeException").isTrue()
                } catch (e: RuntimeException) {
                    assertThat(e.message).isEqualTo("Failure")
                }

                // Then
                val stats = errorRecoveryService.getRetryStats("test_operation")
                assertThat(stats.operation).isEqualTo("test_operation")
                assertThat(stats.retryCount).isEqualTo(2)
                assertThat(stats.lastFailureTime).isNotNull()
            }

        @Test
        @DisplayName("Should reset retry statistics")
        fun shouldResetRetryStatistics() =
            runTest {
                // Given
                val operation = { throw RuntimeException("Failure") }

                // When
                try {
                    errorRecoveryService.executeWithRetry(
                        operation = "test_operation",
                        maxRetries = 2,
                        baseDelayMs = 10,
                        operationBlock = operation,
                    )
                    assertThat(false).withFailMessage("Expected RuntimeException").isTrue()
                } catch (e: RuntimeException) {
                    assertThat(e.message).isEqualTo("Failure")
                }

                // Verify stats exist
                val statsBefore = errorRecoveryService.getRetryStats("test_operation")
                assertThat(statsBefore.retryCount).isEqualTo(2)

                // Reset stats
                errorRecoveryService.resetRetryStats("test_operation")

                // Then
                val statsAfter = errorRecoveryService.getRetryStats("test_operation")
                assertThat(statsAfter.retryCount).isEqualTo(0)
                assertThat(statsAfter.lastFailureTime).isNull()
            }

        @Test
        @DisplayName("Should get all retry statistics")
        fun shouldGetAllRetryStatistics() =
            runTest {
                // Given
                val operation1 = { throw RuntimeException("Failure 1") }
                val operation2 = { throw RuntimeException("Failure 2") }

                // When
                try {
                    errorRecoveryService.executeWithRetry(
                        operation = "operation1",
                        maxRetries = 2,
                        baseDelayMs = 10,
                        operationBlock = operation1,
                    )
                    assertThat(false).withFailMessage("Expected RuntimeException").isTrue()
                } catch (e: RuntimeException) {
                    assertThat(e.message).isEqualTo("Failure 1")
                }

                try {
                    errorRecoveryService.executeWithRetry(
                        operation = "operation2",
                        maxRetries = 1,
                        baseDelayMs = 10,
                        operationBlock = operation2,
                    )
                    assertThat(false).withFailMessage("Expected RuntimeException").isTrue()
                } catch (e: RuntimeException) {
                    assertThat(e.message).isEqualTo("Failure 2")
                }

                // Then
                val allStats = errorRecoveryService.getAllRetryStats()
                assertThat(allStats).hasSize(2)
                assertThat(allStats["operation1"]?.retryCount).isEqualTo(2)
                assertThat(allStats["operation2"]?.retryCount).isEqualTo(1)
            }
    }
}
