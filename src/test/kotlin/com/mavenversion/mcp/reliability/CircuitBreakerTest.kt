package com.mavenversion.mcp.reliability

import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@DisplayName("CircuitBreaker Tests")
class CircuitBreakerTest {

    @Nested
    @DisplayName("Circuit Breaker State Tests")
    inner class CircuitBreakerStateTests {

        @Test
        @DisplayName("Should start in CLOSED state and allow operations")
        fun shouldStartInClosedState() = runTest {
            val circuitBreaker = CircuitBreaker(failureThreshold = 3, recoveryTimeMs = 1000)
            var executed = false

            val result = circuitBreaker.execute {
                executed = true
                "success"
            }

            assertThat(result.isSuccess).isTrue()
            assertThat(result.getOrNull()).isEqualTo("success")
            assertThat(executed).isTrue()
        }

        @Test
        @DisplayName("Should open circuit after failure threshold")
        fun shouldOpenCircuitAfterFailureThreshold() = runTest {
            val circuitBreaker = CircuitBreaker(failureThreshold = 2, recoveryTimeMs = 1000)
            
            // First failure
            val result1 = circuitBreaker.execute {
                throw RuntimeException("Failure 1")
            }
            assertThat(result1.isFailure).isTrue()

            // Second failure - should open circuit
            val result2 = circuitBreaker.execute {
                throw RuntimeException("Failure 2")
            }
            assertThat(result2.isFailure).isTrue()

            // Third attempt - should be rejected by open circuit
            var executed = false
            val result3 = circuitBreaker.execute {
                executed = true
                "should not execute"
            }

            assertThat(result3.isFailure).isTrue()
            assertThat(result3.exceptionOrNull()).isInstanceOf(CircuitBreakerOpenException::class.java)
            assertThat(executed).isFalse()
        }

        @Test
        @DisplayName("Should transition to HALF_OPEN after recovery time")
        fun shouldTransitionToHalfOpenAfterRecoveryTime() = runTest {
            val circuitBreaker = CircuitBreaker(failureThreshold = 1, recoveryTimeMs = 100)
            
            // Cause failure to open circuit
            circuitBreaker.execute {
                throw RuntimeException("Failure")
            }

            // Verify circuit is open
            val result1 = circuitBreaker.execute { "should not execute" }
            assertThat(result1.isFailure).isTrue()
            assertThat(result1.exceptionOrNull()).isInstanceOf(CircuitBreakerOpenException::class.java)

            // Wait for recovery time
            delay(150)

            // Should now allow execution (HALF_OPEN state)
            var executed = false
            val result2 = circuitBreaker.execute {
                executed = true
                "success"
            }

            assertThat(result2.isSuccess).isTrue()
            assertThat(executed).isTrue()
        }

        @Test
        @DisplayName("Should close circuit on successful operation in HALF_OPEN state")
        fun shouldCloseCircuitOnSuccessInHalfOpenState() = runTest {
            val circuitBreaker = CircuitBreaker(failureThreshold = 1, recoveryTimeMs = 100)
            
            // Open circuit
            circuitBreaker.execute { throw RuntimeException("Failure") }
            
            // Wait for recovery
            delay(150)
            
            // Successful operation should close circuit
            circuitBreaker.execute { "success" }
            
            // Subsequent operations should work normally
            val result = circuitBreaker.execute { "normal operation" }
            assertThat(result.isSuccess).isTrue()
            assertThat(result.getOrNull()).isEqualTo("normal operation")
        }

        @Test
        @DisplayName("Should reopen circuit on failure in HALF_OPEN state")
        fun shouldReopenCircuitOnFailureInHalfOpenState() = runTest {
            val circuitBreaker = CircuitBreaker(failureThreshold = 1, recoveryTimeMs = 100)
            
            // Open circuit
            circuitBreaker.execute { throw RuntimeException("Initial failure") }
            
            // Wait for recovery
            delay(150)
            
            // Failure in HALF_OPEN should reopen circuit
            val result1 = circuitBreaker.execute {
                throw RuntimeException("Failure in half-open")
            }
            assertThat(result1.isFailure).isTrue()
            
            // Circuit should be open again
            val result2 = circuitBreaker.execute { "should not execute" }
            assertThat(result2.isFailure).isTrue()
            assertThat(result2.exceptionOrNull()).isInstanceOf(CircuitBreakerOpenException::class.java)
        }
    }

    @Nested
    @DisplayName("Circuit Breaker Configuration Tests")
    inner class CircuitBreakerConfigurationTests {

        @Test
        @DisplayName("Should respect custom failure threshold")
        fun shouldRespectCustomFailureThreshold() = runTest {
            val circuitBreaker = CircuitBreaker(failureThreshold = 3, recoveryTimeMs = 1000)
            
            // First two failures should not open circuit
            repeat(2) {
                val result = circuitBreaker.execute {
                    throw RuntimeException("Failure $it")
                }
                assertThat(result.isFailure).isTrue()
            }
            
            // Circuit should still be closed
            val result = circuitBreaker.execute { "should execute" }
            assertThat(result.isSuccess).isTrue()
            
            // Third failure should open circuit
            circuitBreaker.execute { throw RuntimeException("Third failure") }
            
            // Now circuit should be open
            val blockedResult = circuitBreaker.execute { "should not execute" }
            assertThat(blockedResult.isFailure).isTrue()
            assertThat(blockedResult.exceptionOrNull()).isInstanceOf(CircuitBreakerOpenException::class.java)
        }

        @Test
        @DisplayName("Should respect custom recovery time")
        fun shouldRespectCustomRecoveryTime() = runTest {
            val circuitBreaker = CircuitBreaker(failureThreshold = 1, recoveryTimeMs = 200)
            
            // Open circuit
            circuitBreaker.execute { throw RuntimeException("Failure") }
            
            // Should still be open before recovery time
            delay(100)
            val result1 = circuitBreaker.execute { "should not execute" }
            assertThat(result1.isFailure).isTrue()
            assertThat(result1.exceptionOrNull()).isInstanceOf(CircuitBreakerOpenException::class.java)
            
            // Should allow execution after recovery time
            delay(150) // Total 250ms > 200ms recovery time
            val result2 = circuitBreaker.execute { "should execute" }
            assertThat(result2.isSuccess).isTrue()
        }
    }
}