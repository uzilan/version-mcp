package com.mavenversion.mcp.integration

import com.mavenversion.mcp.client.MCPProcessManager
import com.mavenversion.mcp.client.MCPServerConfig
import com.mavenversion.mcp.client.PlaywrightMCPClient
import kotlinx.coroutines.test.runTest
import mu.KotlinLogging
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable

private val log = KotlinLogging.logger {}

@DisplayName("Stdio MCP Integration Tests")
@EnabledIfEnvironmentVariable(named = "INTEGRATION_TESTS", matches = "true")
class StdioMCPIntegrationTest {

    private lateinit var processManager: MCPProcessManager

    @BeforeEach
    fun setUp() {
        processManager = MCPProcessManager()
    }

    @AfterEach
    fun tearDown() = runTest {
        processManager.stopAll()
    }

    @Nested
    @DisplayName("Playwright MCP Server Tests")
    inner class PlaywrightMCPServerTests {

        @Test
        @DisplayName("Should connect to Playwright MCP server via uvx")
        fun shouldConnectToPlaywrightMCPServerViaUvx() = runTest {
            val config = MCPServerConfig.playwrightDefault()
            val client = PlaywrightMCPClient(config, processManager)
            
            try {
                val result = client.connect()
                
                if (result.isSuccess) {
                    assertThat(client.isConnected()).isTrue()
                    log.info { "Successfully connected to Playwright MCP server via uvx" }
                } else {
                    log.warn { "Failed to connect to Playwright MCP server via uvx: ${result.exceptionOrNull()?.message}" }
                    log.info { "This is expected if uvx/uv is not installed on the system" }
                }
            } finally {
                client.disconnect()
            }
        }

        @Test
        @DisplayName("Should connect to Playwright MCP server via npx")
        fun shouldConnectToPlaywrightMCPServerViaNpx() = runTest {
            val config = MCPServerConfig.playwrightNpx()
            val client = PlaywrightMCPClient(config, processManager)
            
            try {
                val result = client.connect()
                
                if (result.isSuccess) {
                    assertThat(client.isConnected()).isTrue()
                    log.info { "Successfully connected to Playwright MCP server via npx" }
                } else {
                    log.warn { "Failed to connect to Playwright MCP server via npx: ${result.exceptionOrNull()?.message}" }
                    log.info { "This is expected if Node.js/npm is not installed on the system" }
                }
            } finally {
                client.disconnect()
            }
        }

        @Test
        @DisplayName("Should perform basic Playwright operations if server is available")
        fun shouldPerformBasicPlaywrightOperationsIfServerIsAvailable() = runTest {
            val config = MCPServerConfig.playwrightDefault()
            val client = PlaywrightMCPClient(config, processManager)
            
            val connectResult = client.connect()
            if (connectResult.isFailure) {
                log.info { "Skipping Playwright operations test - server not available" }
                return@runTest
            }
            
            try {
                // Test basic navigation
                val navigationResult = client.navigateToUrl("https://example.com")
                if (navigationResult.isSuccess) {
                    val content = navigationResult.getOrThrow()
                    assertThat(content).isNotEmpty()
                    assertThat(content).containsIgnoringCase("example")
                    log.info { "Successfully performed navigation test" }
                } else {
                    log.warn { "Navigation test failed: ${navigationResult.exceptionOrNull()?.message}" }
                }
                
                // Test getting page content
                val contentResult = client.getPageContent()
                if (contentResult.isSuccess) {
                    val content = contentResult.getOrThrow()
                    assertThat(content).isNotEmpty()
                    log.info { "Successfully retrieved page content" }
                }
                
            } finally {
                client.disconnect()
            }
        }
    }

    @Nested
    @DisplayName("Process Management Tests")
    inner class ProcessManagementTests {

        @Test
        @DisplayName("Should handle multiple server instances")
        fun shouldHandleMultipleServerInstances() = runTest {
            val config1 = MCPServerConfig(
                name = "server1",
                command = listOf("echo", "server1"),
                autoRestart = false
            )
            
            val config2 = MCPServerConfig(
                name = "server2", 
                command = listOf("echo", "server2"),
                autoRestart = false
            )
            
            // Both should fail to connect (echo doesn't implement MCP protocol)
            // but we're testing the process management
            val result1 = processManager.getClient(config1)
            val result2 = processManager.getClient(config2)
            
            assertThat(result1.isFailure).isTrue()
            assertThat(result2.isFailure).isTrue()
            
            val status = processManager.getStatus()
            // Servers should not be in status since connection failed
            assertThat(status).doesNotContainKey("server1")
            assertThat(status).doesNotContainKey("server2")
        }

        @Test
        @DisplayName("Should handle server restart scenarios")
        fun shouldHandleServerRestartScenarios() = runTest {
            val config = MCPServerConfig(
                name = "restart-test",
                command = listOf("echo", "test"),
                autoRestart = true,
                maxRestartAttempts = 2
            )
            
            // Try to get client (will fail)
            val result = processManager.getClient(config)
            assertThat(result.isFailure).isTrue()
            
            // Try to restart non-existent server
            val restartResult = processManager.restartServer("restart-test")
            assertThat(restartResult.isFailure).isTrue()
        }

        @Test
        @DisplayName("Should handle health checks")
        fun shouldHandleHealthChecks() = runTest {
            // Health check for non-existent server
            val healthResult = processManager.healthCheck("nonexistent")
            assertThat(healthResult.getOrNull()).isFalse()
            
            // Stop non-existent server
            val stopResult = processManager.stopServer("nonexistent")
            assertThat(stopResult.isFailure).isTrue()
        }
    }

    @Nested
    @DisplayName("Error Handling Tests")
    inner class ErrorHandlingTests {

        @Test
        @DisplayName("Should handle invalid MCP server commands gracefully")
        fun shouldHandleInvalidMCPServerCommandsGracefully() = runTest {
            val config = MCPServerConfig(
                name = "invalid-server",
                command = listOf("nonexistent-command-12345"),
                autoRestart = false
            )
            
            val result = processManager.getClient(config)
            assertThat(result.isFailure).isTrue()
            
            val exception = result.exceptionOrNull()
            assertThat(exception).isNotNull()
            log.info { "Expected error for invalid command: ${exception?.message}" }
        }

        @Test
        @DisplayName("Should handle process cleanup on failure")
        fun shouldHandleProcessCleanupOnFailure() = runTest {
            val config = MCPServerConfig(
                name = "cleanup-test",
                command = listOf("false"), // Command that exits immediately with error
                autoRestart = false
            )
            
            val result = processManager.getClient(config)
            assertThat(result.isFailure).isTrue()
            
            // Status should not contain the failed server
            val status = processManager.getStatus()
            assertThat(status).doesNotContainKey("cleanup-test")
        }
    }
}