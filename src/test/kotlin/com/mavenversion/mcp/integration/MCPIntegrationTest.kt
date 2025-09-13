package com.mavenversion.mcp.integration

import com.mavenversion.mcp.client.MCPProcessManager
import com.mavenversion.mcp.client.MCPServerConfig
import com.mavenversion.mcp.client.PlaywrightMCPClient
import com.mavenversion.mcp.web.MavenRepositoryClient
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable

@DisplayName("MCP Integration Tests")
class MCPIntegrationTest {
    @Test
    @DisplayName("Should create MCP client instances")
    fun shouldCreateMCPClientInstances() = runTest {
        val config = MCPServerConfig(
            name = "test-playwright",
            command = listOf("echo", "test"),
            autoRestart = false
        )
        val processManager = MCPProcessManager()
        val playwrightClient = PlaywrightMCPClient(config, processManager)
        val mavenRepositoryClient = MavenRepositoryClient(playwrightClient)

        assertThat(playwrightClient).isNotNull()
        assertThat(mavenRepositoryClient).isNotNull()
        assertThat(playwrightClient.isConnected()).isFalse()
        assertThat(mavenRepositoryClient.isConnected()).isFalse()
        
        processManager.stopAll()
    }

    @Test
    @DisplayName("Should handle connection attempts gracefully")
    fun shouldHandleConnectionAttemptsGracefully() =
        runTest {
            val config = MCPServerConfig(
                name = "test-playwright",
                command = listOf("echo", "test"),
                autoRestart = false
            )
            val processManager = MCPProcessManager()
            val playwrightClient = PlaywrightMCPClient(config, processManager)
            val mavenRepositoryClient = MavenRepositoryClient(playwrightClient)

            // These will fail in test environment but should not throw exceptions
            val connectResult = playwrightClient.connect()
            val initResult = mavenRepositoryClient.initialize()

            assertThat(connectResult.isFailure).isTrue()
            assertThat(initResult.isFailure).isTrue()

            // Cleanup
            playwrightClient.disconnect()
            mavenRepositoryClient.close()
            processManager.stopAll()
        }

    @Test
    @EnabledIfEnvironmentVariable(named = "INTEGRATION_TESTS", matches = "true")
    @DisplayName("Should connect to live MCP server when available")
    fun shouldConnectToLiveMCPServerWhenAvailable() =
        runTest {
            val config = MCPServerConfig.playwrightDefault()
            val processManager = MCPProcessManager()
            val playwrightClient = PlaywrightMCPClient(config, processManager)
            val mavenRepositoryClient = MavenRepositoryClient(playwrightClient)

            try {
                val initResult = mavenRepositoryClient.initialize()

                if (initResult.isSuccess) {
                    assertThat(mavenRepositoryClient.isConnected()).isTrue()

                    // Test basic navigation
                    val navResult = mavenRepositoryClient.navigateToHomepage()
                    assertThat(navResult.isSuccess).isTrue()
                }
            } finally {
                mavenRepositoryClient.close()
                processManager.stopAll()
            }
        }
}
