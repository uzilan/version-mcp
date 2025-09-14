package com.mavenversion.mcp.examples

import com.mavenversion.mcp.reliability.ReliabilityService
import kotlinx.coroutines.runBlocking
import mu.KotlinLogging

private val log = KotlinLogging.logger {}

/**
 * Demonstration of the reliability features
 */
fun main() =
    runBlocking {
        log.info { "Starting Reliability Features Demo" }

        val reliabilityService =
            ReliabilityService(
                maxRetries = 3,
                baseDelayMs = 1000,
                rateLimitDelayMs = 2000,
            )

        // Demo 1: Successful operation
        log.info { "Demo 1: Successful operation" }
        val result1 =
            reliabilityService.executeWithRetry("demo operation") {
                "Success!"
            }
        log.info { "Result 1: ${result1.getOrNull()}" }

        // Demo 2: Operation with retries
        log.info { "Demo 2: Operation with retries" }
        var attempts = 0
        val result2 =
            reliabilityService.executeWithRetry(
                operation = "retry demo",
                retryableExceptions = setOf(RuntimeException::class.java),
            ) {
                attempts++
                if (attempts < 3) {
                    throw RuntimeException("Temporary failure $attempts")
                }
                "Success after $attempts attempts!"
            }
        log.info { "Result 2: ${result2.getOrNull()}" }

        // Demo 3: Rate limiting
        log.info { "Demo 3: Rate limiting demonstration" }
        val startTime = System.currentTimeMillis()
        repeat(3) { i ->
            reliabilityService.executeWithRetry("rate limited operation $i") {
                "Operation $i completed"
            }
        }
        val endTime = System.currentTimeMillis()
        log.info { "Rate limited operations completed in ${endTime - startTime}ms" }

        log.info { "Reliability Features Demo completed successfully!" }
    }
