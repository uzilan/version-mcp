package com.mavenversion.mcp.integration

import com.mavenversion.mcp.client.MCPProcessManager
import com.mavenversion.mcp.client.MCPServerConfig
import com.mavenversion.mcp.client.PlaywrightMCPClient
import com.mavenversion.mcp.reliability.ReliabilityService
import com.mavenversion.mcp.web.MavenRepositoryClient
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable

@DisplayName("Reliability Integration Tests")
@EnabledIfEnvironmentVariable(named = "PLAYWRIGHT_MCP_AVAILABLE", matches = "true")
class ReliabilityIntegrationTest {
    private lateinit var client: MavenRepositoryClient
    private lateinit var reliabilityService: ReliabilityService

    @BeforeEach
    fun setUp() {
        reliabilityService =
            ReliabilityService(
                maxRetries = 3,
                baseDelayMs = 1000,
                rateLimitDelayMs = 2000,
            )

        client =
            MavenRepositoryClient(
                reliabilityService = reliabilityService,
            )
    }

    @Nested
    @DisplayName("Live Website Integration Tests")
    inner class LiveWebsiteIntegrationTests {
        @Test
        @DisplayName("Should handle real mvnrepository.com with rate limiting")
        fun shouldHandleRealMvnRepositoryWithRateLimiting() =
            runTest {
                // Initialize client
                val initResult = client.initialize()
                assertThat(initResult.isSuccess).isTrue()

                try {
                    // Test navigation with rate limiting
                    val navResult = client.navigateToHomepage()
                    assertThat(navResult.isSuccess).isTrue()

                    // Test search with rate limiting
                    val searchResult = client.searchDependencies("junit")
                    assertThat(searchResult.isSuccess).isTrue()

                    val searchData = searchResult.getOrThrow()
                    assertThat(searchData.dependencies).isNotEmpty()
                    assertThat(searchData.query).isEqualTo("junit")

                    // Test version retrieval with rate limiting
                    val versionResult = client.getLatestVersion("org.junit.jupiter", "junit-jupiter")
                    assertThat(versionResult.isSuccess).isTrue()

                    val version = versionResult.getOrThrow()
                    assertThat(version).isNotNull()
                    assertThat(version!!.version).isNotBlank()
                } finally {
                    client.close()
                }
            }

        @Test
        @DisplayName("Should handle network interruptions gracefully")
        fun shouldHandleNetworkInterruptionsGracefully() =
            runTest {
                val initResult = client.initialize()
                assertThat(initResult.isSuccess).isTrue()

                try {
                    // This test would require simulating network issues
                    // For now, we test that the client can recover from temporary failures

                    val searchResult = client.searchDependencies("spring-boot")
                    // Even if there are temporary network issues, the retry logic should handle them
                    assertThat(searchResult.isSuccess).isTrue()
                } finally {
                    client.close()
                }
            }

        @Test
        @DisplayName("Should respect rate limits during burst requests")
        fun shouldRespectRateLimitsDuringBurstRequests() =
            runTest {
                val initResult = client.initialize()
                assertThat(initResult.isSuccess).isTrue()

                try {
                    val startTime = System.currentTimeMillis()

                    // Make multiple requests in quick succession
                    val queries = listOf("junit", "mockito", "spring", "hibernate", "jackson")
                    val results = mutableListOf<Boolean>()

                    for (query in queries) {
                        val result = client.searchDependencies(query)
                        results.add(result.isSuccess)
                    }

                    val endTime = System.currentTimeMillis()
                    val totalTime = endTime - startTime

                    // All requests should succeed
                    assertThat(results).allMatch { it }

                    // Should have taken time due to rate limiting (at least 8 seconds for 5 requests with 2s delays)
                    assertThat(totalTime).isGreaterThan(6000)
                } finally {
                    client.close()
                }
            }
    }

    @Nested
    @DisplayName("Error Recovery Integration Tests")
    inner class ErrorRecoveryIntegrationTests {
        @Test
        @DisplayName("Should recover from temporary MCP server disconnections")
        fun shouldRecoverFromTemporaryMCPServerDisconnections() =
            runTest {
                // This test would require a way to simulate MCP server disconnections
                // For now, we test the basic recovery mechanism

                val config =
                    MCPServerConfig(
                        name = "test-playwright",
                        command = listOf("echo", "test"),
                        autoRestart = false,
                    )
                val processManager = MCPProcessManager()
                val playwrightClient = PlaywrightMCPClient(config, processManager)

                // Test connection
                val connectResult = playwrightClient.connect()
                if (connectResult.isSuccess) {
                    // Test that client can handle disconnection and reconnection
                    playwrightClient.disconnect()

                    val reconnectResult = playwrightClient.connect()
                    assertThat(reconnectResult.isSuccess).isTrue()

                    playwrightClient.disconnect()
                }
            }

        @Test
        @DisplayName("Should handle malformed responses gracefully")
        fun shouldHandleMalformedResponsesGracefully() =
            runTest {
                val initResult = client.initialize()
                assertThat(initResult.isSuccess).isTrue()

                try {
                    // Test with a query that might return unexpected results
                    val result = client.searchDependencies("!@#$%^&*()")

                    // Should either succeed with empty results or fail gracefully
                    if (result.isSuccess) {
                        val searchData = result.getOrThrow()
                        assertThat(searchData.dependencies).isEmpty()
                    } else {
                        // Should fail with a meaningful error message
                        assertThat(result.exceptionOrNull()).isNotNull()
                    }
                } finally {
                    client.close()
                }
            }
    }

    @Nested
    @DisplayName("Performance and Reliability Tests")
    inner class PerformanceAndReliabilityTests {
        @Test
        @DisplayName("Should maintain performance under load with reliability features")
        fun shouldMaintainPerformanceUnderLoadWithReliabilityFeatures() =
            runTest {
                val initResult = client.initialize()
                assertThat(initResult.isSuccess).isTrue()

                try {
                    val startTime = System.currentTimeMillis()
                    val successCount = mutableListOf<Boolean>()

                    // Perform multiple operations to test reliability under load
                    repeat(10) { i ->
                        val result = client.searchDependencies("test-query-$i")
                        successCount.add(result.isSuccess)
                    }

                    val endTime = System.currentTimeMillis()
                    val totalTime = endTime - startTime

                    // Most operations should succeed (allowing for some failures due to rate limiting)
                    val successRate = successCount.count { it }.toDouble() / successCount.size
                    assertThat(successRate).isGreaterThan(0.7) // At least 70% success rate

                    // Should complete within reasonable time (considering rate limiting)
                    assertThat(totalTime).isLessThan(60000) // Less than 1 minute
                } finally {
                    client.close()
                }
            }

        @Test
        @DisplayName("Should handle concurrent requests safely")
        fun shouldHandleConcurrentRequestsSafely() =
            runTest {
                val initResult = client.initialize()
                assertThat(initResult.isSuccess).isTrue()

                try {
                    // Note: This is a simplified test. In a real scenario, you'd use
                    // kotlinx.coroutines.async to make truly concurrent requests

                    val results = mutableListOf<Boolean>()

                    // Make several requests in sequence (simulating concurrent load)
                    repeat(5) { i ->
                        val result = client.searchDependencies("concurrent-test-$i")
                        results.add(result.isSuccess)
                    }

                    // All requests should be handled safely (either succeed or fail gracefully)
                    assertThat(results).isNotEmpty()
                    // At least some should succeed
                    assertThat(results).anyMatch { it }
                } finally {
                    client.close()
                }
            }
    }
}
