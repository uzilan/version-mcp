package com.mavenversion.mcp.reliability

import kotlinx.coroutines.delay
import mu.KotlinLogging
import kotlin.math.min
import kotlin.math.pow
import kotlin.random.Random

private val log = KotlinLogging.logger {}

/**
 * Service for handling reliability features like retry logic, rate limiting, and error handling
 */
class ReliabilityService(
    private val maxRetries: Int = 3,
    private val baseDelayMs: Long = 1000,
    private val maxDelayMs: Long = 30000,
    private val jitterFactor: Double = 0.1,
    private val rateLimitDelayMs: Long = 2000,
) {
    private var lastRequestTime: Long = 0
    private val requestHistory = mutableListOf<Long>()
    private val maxRequestsPerMinute = 30 // Conservative rate limit

    /**
     * Execute an operation with retry logic and exponential backoff
     */
    suspend fun <T> executeWithRetry(
        operation: String,
        retryableExceptions: Set<Class<out Exception>> = setOf(
            Exception::class.java
        ),
        block: suspend () -> T,
    ): Result<T> =
        runCatching {
            var lastException: Exception? = null

            repeat(maxRetries) { attempt ->
                try {
                    // Apply rate limiting before each attempt
                    applyRateLimit()
                    
                    return@runCatching block()
                } catch (e: Exception) {
                    lastException = e
                    
                    // Check if this exception type should be retried
                    val shouldRetry = retryableExceptions.any { it.isAssignableFrom(e::class.java) } ||
                                    isRetryableException(e)
                    
                    if (!shouldRetry) {
                        log.warn { "Non-retryable exception for $operation: ${e.message}" }
                        throw e
                    }
                    
                    log.warn { "Attempt ${attempt + 1} failed for $operation: ${e.message}" }

                    if (attempt < maxRetries - 1) {
                        val delayMs = calculateBackoffDelay(attempt)
                        log.debug { "Retrying $operation in ${delayMs}ms" }
                        delay(delayMs)
                    }
                }
            }

            throw lastException ?: Exception("Unknown error during $operation")
        }.onFailure { error ->
            log.error(error) { "Failed to execute $operation after $maxRetries attempts" }
        }

    /**
     * Apply rate limiting to respect website limits
     */
    private suspend fun applyRateLimit() {
        // Skip rate limiting if disabled (rateLimitDelayMs = 0)
        if (rateLimitDelayMs <= 0) {
            return
        }
        
        val currentTime = System.currentTimeMillis()
        
        // Clean old requests (older than 1 minute)
        requestHistory.removeAll { it < currentTime - 60000 }
        
        // Check if we're hitting rate limits
        if (requestHistory.size >= maxRequestsPerMinute) {
            val oldestRequest = requestHistory.minOrNull() ?: currentTime
            val waitTime = 60000 - (currentTime - oldestRequest)
            if (waitTime > 0) {
                log.info { "Rate limit reached, waiting ${waitTime}ms" }
                delay(waitTime)
            }
        }
        
        // Ensure minimum delay between requests
        val timeSinceLastRequest = currentTime - lastRequestTime
        if (timeSinceLastRequest < rateLimitDelayMs) {
            val delayNeeded = rateLimitDelayMs - timeSinceLastRequest
            log.debug { "Applying rate limit delay: ${delayNeeded}ms" }
            delay(delayNeeded)
        }
        
        // Record this request
        lastRequestTime = System.currentTimeMillis()
        requestHistory.add(lastRequestTime)
    }

    /**
     * Calculate exponential backoff delay with jitter
     */
    private fun calculateBackoffDelay(attempt: Int): Long {
        val exponentialDelay = baseDelayMs * 2.0.pow(attempt).toLong()
        val jitter = (Random.nextDouble() * jitterFactor * exponentialDelay).toLong()
        return min(exponentialDelay + jitter, maxDelayMs)
    }

    /**
     * Check if an exception indicates a temporary failure that should be retried
     */
    fun isRetryableException(exception: Exception): Boolean {
        val message = exception.message?.lowercase() ?: ""
        
        return when {
            // Network-related errors
            message.contains("timeout") -> true
            message.contains("connection") -> true
            message.contains("network") -> true
            message.contains("socket") -> true
            
            // HTTP errors that might be temporary
            message.contains("502") -> true // Bad Gateway
            message.contains("503") -> true // Service Unavailable
            message.contains("504") -> true // Gateway Timeout
            message.contains("429") -> true // Too Many Requests
            
            // Playwright-specific errors that might be temporary
            message.contains("page crashed") -> true
            message.contains("browser disconnected") -> true
            message.contains("navigation timeout") -> true
            
            // Website structure changes (might be temporary)
            message.contains("element not found") -> true
            message.contains("selector not found") -> true
            
            else -> false
        }
    }

    /**
     * Handle website structure change errors gracefully
     */
    fun handleStructureChangeError(
        operation: String,
        selector: String,
        exception: Exception
    ): Result<String> {
        log.warn { "Possible website structure change detected in $operation for selector '$selector': ${exception.message}" }
        
        return Result.failure(
            WebsiteStructureException(
                "Website structure may have changed. Selector '$selector' not found during $operation. " +
                "This might be a temporary issue or the website layout may have been updated.",
                exception
            )
        )
    }

    /**
     * Create a circuit breaker for repeated failures
     */
    fun createCircuitBreaker(
        failureThreshold: Int = 5,
        recoveryTimeMs: Long = 60000
    ): CircuitBreaker {
        return CircuitBreaker(failureThreshold, recoveryTimeMs)
    }
}

/**
 * Circuit breaker to prevent cascading failures
 */
class CircuitBreaker(
    private val failureThreshold: Int,
    private val recoveryTimeMs: Long
) {
    private var failureCount = 0
    private var lastFailureTime = 0L
    private var state = State.CLOSED

    enum class State { CLOSED, OPEN, HALF_OPEN }

    suspend fun <T> execute(operation: suspend () -> T): Result<T> {
        return when (state) {
            State.OPEN -> {
                if (System.currentTimeMillis() - lastFailureTime > recoveryTimeMs) {
                    state = State.HALF_OPEN
                    log.info { "Circuit breaker transitioning to HALF_OPEN state" }
                    executeOperation(operation)
                } else {
                    Result.failure(CircuitBreakerOpenException("Circuit breaker is OPEN"))
                }
            }
            State.HALF_OPEN -> {
                executeOperation(operation)
            }
            State.CLOSED -> {
                executeOperation(operation)
            }
        }
    }

    private suspend fun <T> executeOperation(operation: suspend () -> T): Result<T> {
        return try {
            val result = operation()
            onSuccess()
            Result.success(result)
        } catch (e: Exception) {
            onFailure()
            Result.failure(e)
        }
    }

    private fun onSuccess() {
        failureCount = 0
        state = State.CLOSED
        log.debug { "Circuit breaker reset to CLOSED state" }
    }

    private fun onFailure() {
        failureCount++
        lastFailureTime = System.currentTimeMillis()
        
        if (failureCount >= failureThreshold) {
            state = State.OPEN
            log.warn { "Circuit breaker opened due to $failureCount failures" }
        }
    }
}

/**
 * Exception thrown when website structure changes are detected
 */
class WebsiteStructureException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * Exception thrown when circuit breaker is open
 */
class CircuitBreakerOpenException(message: String) : Exception(message)