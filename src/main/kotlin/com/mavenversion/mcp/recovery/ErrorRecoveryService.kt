package com.mavenversion.mcp.recovery

import com.mavenversion.mcp.logging.StructuredLoggingService
import kotlinx.coroutines.delay
import mu.KotlinLogging
import java.time.Duration
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

private val log = KotlinLogging.logger {}

/**
 * Service for handling error recovery and retry mechanisms
 */
class ErrorRecoveryService(
    private val loggingService: StructuredLoggingService,
) {
    private val retryCounters = ConcurrentHashMap<String, AtomicInteger>()
    private val lastFailureTimes = ConcurrentHashMap<String, Instant>()
    private val circuitBreakerStates = ConcurrentHashMap<String, CircuitBreakerState>()

    /**
     * Execute operation with retry logic
     */
    suspend fun <T> executeWithRetry(
        operation: String,
        maxRetries: Int = 3,
        baseDelayMs: Long = 1000,
        maxDelayMs: Long = 10000,
        backoffMultiplier: Double = 2.0,
        operationBlock: suspend () -> T,
    ): T {
        var lastException: Exception? = null
        var delayMs = baseDelayMs

        for (attempt in 1..maxRetries) {
            try {
                loggingService.logMCPOperation(
                    operation = operation,
                    details = mapOf("attempt" to attempt.toString(), "maxRetries" to maxRetries.toString()),
                )

                val result = operationBlock()

                // Success - reset retry counter
                retryCounters.remove(operation)
                lastFailureTimes.remove(operation)

                loggingService.logMCPOperation(
                    operation = operation,
                    details = mapOf("attempt" to attempt.toString(), "success" to "true"),
                    success = true,
                )

                return result
            } catch (e: Exception) {
                lastException = e
                val retryCount = retryCounters.computeIfAbsent(operation) { AtomicInteger(0) }.incrementAndGet()
                lastFailureTimes[operation] = Instant.now()

                loggingService.logMCPOperation(
                    operation = operation,
                    details = mapOf("attempt" to attempt.toString(), "retryCount" to retryCount.toString()),
                    success = false,
                    error = e,
                )

                if (attempt == maxRetries) {
                    log.error(e) { "Operation '$operation' failed after $maxRetries attempts" }
                    break
                }

                // Check if we should use exponential backoff
                if (delayMs < maxDelayMs) {
                    delayMs = (delayMs * backoffMultiplier).toLong().coerceAtMost(maxDelayMs)
                }

                log.warn { "Operation '$operation' failed (attempt $attempt/$maxRetries), retrying in ${delayMs}ms" }
                delay(delayMs)
            }
        }

        throw lastException ?: RuntimeException("Operation failed without exception")
    }

    /**
     * Execute operation with circuit breaker pattern
     */
    suspend fun <T> executeWithCircuitBreaker(
        operation: String,
        failureThreshold: Int = 5,
        recoveryTimeout: Duration = Duration.ofMinutes(1),
        operationBlock: suspend () -> T,
    ): T {
        val circuitBreaker =
            circuitBreakerStates.computeIfAbsent(operation) {
                CircuitBreakerState(failureThreshold, recoveryTimeout)
            }

        return when (circuitBreaker.getState()) {
            CircuitBreakerState.State.CLOSED -> {
                try {
                    val result = operationBlock()
                    circuitBreaker.onSuccess()
                    result
                } catch (e: Exception) {
                    circuitBreaker.onFailure()
                    throw e
                }
            }
            CircuitBreakerState.State.OPEN -> {
                if (circuitBreaker.shouldAttemptReset()) {
                    circuitBreaker.setState(CircuitBreakerState.State.HALF_OPEN)
                    try {
                        val result = operationBlock()
                        circuitBreaker.onSuccess()
                        result
                    } catch (e: Exception) {
                        circuitBreaker.onFailure()
                        throw e
                    }
                } else {
                    throw CircuitBreakerOpenException("Circuit breaker is open for operation: $operation")
                }
            }
            CircuitBreakerState.State.HALF_OPEN -> {
                try {
                    val result = operationBlock()
                    circuitBreaker.onSuccess()
                    result
                } catch (e: Exception) {
                    circuitBreaker.onFailure()
                    throw e
                }
            }
        }
    }

    /**
     * Execute operation with timeout
     */
    suspend fun <T> executeWithTimeout(
        operation: String,
        timeoutMs: Long,
        operationBlock: suspend () -> T,
    ): T {
        return try {
            kotlinx.coroutines.withTimeout(timeoutMs) {
                operationBlock()
            }
        } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
            loggingService.logMCPOperation(
                operation = operation,
                details = mapOf("timeoutMs" to timeoutMs.toString()),
                success = false,
                error = e,
            )
            throw java.util.concurrent.TimeoutException("Operation '$operation' timed out after ${timeoutMs}ms")
        }
    }

    /**
     * Get retry statistics for an operation
     */
    fun getRetryStats(operation: String): RetryStats {
        val retryCount = retryCounters[operation]?.get() ?: 0
        val lastFailure = lastFailureTimes[operation]
        val circuitBreaker = circuitBreakerStates[operation]

        return RetryStats(
            operation = operation,
            retryCount = retryCount,
            lastFailureTime = lastFailure,
            circuitBreakerState = circuitBreaker?.getState(),
            failureCount = circuitBreaker?.failureCount ?: 0,
        )
    }

    /**
     * Reset retry statistics for an operation
     */
    fun resetRetryStats(operation: String) {
        retryCounters.remove(operation)
        lastFailureTimes.remove(operation)
        circuitBreakerStates.remove(operation)
        log.info { "Reset retry statistics for operation: $operation" }
    }

    /**
     * Get all retry statistics
     */
    fun getAllRetryStats(): Map<String, RetryStats> {
        return retryCounters.keys.associateWith { getRetryStats(it) }
    }
}

/**
 * Circuit breaker state management
 */
class CircuitBreakerState(
    private val failureThreshold: Int,
    private val recoveryTimeout: Duration,
) {
    enum class State {
        CLOSED, // Normal operation
        OPEN, // Circuit is open, failing fast
        HALF_OPEN, // Testing if service has recovered
    }

    private var state: State = State.CLOSED

    fun getState(): State = state

    fun setState(newState: State) {
        state = newState
    }

    var failureCount: Int = 0
        private set

    private var lastFailureTime: Instant? = null

    fun onSuccess() {
        failureCount = 0
        setState(State.CLOSED)
        lastFailureTime = null
    }

    fun onFailure() {
        failureCount++
        lastFailureTime = Instant.now()

        if (failureCount >= failureThreshold) {
            setState(State.OPEN)
        }
    }

    fun shouldAttemptReset(): Boolean {
        return lastFailureTime?.let {
            Duration.between(it, Instant.now()) >= recoveryTimeout
        } ?: true
    }
}

/**
 * Retry statistics
 */
data class RetryStats(
    val operation: String,
    val retryCount: Int,
    val lastFailureTime: Instant?,
    val circuitBreakerState: CircuitBreakerState.State?,
    val failureCount: Int,
)

/**
 * Exception thrown when circuit breaker is open
 */
class CircuitBreakerOpenException(message: String) : RuntimeException(message)
