package com.mavenversion.mcp.integration

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
    fun shouldCreateMCPClientInstances() {
        val playwrightClient = PlaywrightMCPClient()
        val mavenRepositoryClient = MavenRepositoryClient(playwrightClient)

        assertThat(playwrightClient).isNotNull()
        assertThat(mavenRepositoryClient).isNotNull()
        assertThat(playwrightClient.isConnected()).isFalse()
        assertThat(mavenRepositoryClient.isConnected()).isFalse()
    }

    @Test
    @DisplayName("Should handle connection attempts gracefully")
    fun shouldHandleConnectionAttemptsGracefully() =
        runTest {
            val playwrightClient = PlaywrightMCPClient()
            val mavenRepositoryClient = MavenRepositoryClient(playwrightClient)

            // These will fail in test environment but should not throw exceptions
            val connectResult = playwrightClient.connect()
            val initResult = mavenRepositoryClient.initialize()

            assertThat(connectResult.isFailure).isTrue()
            assertThat(initResult.isFailure).isTrue()

            // Cleanup
            playwrightClient.disconnect()
            mavenRepositoryClient.close()
        }

    @Test
    @EnabledIfEnvironmentVariable(named = "MCP_SERVER_RUNNING", matches = "true")
    @DisplayName("Should connect to live MCP server when available")
    fun shouldConnectToLiveMCPServerWhenAvailable() =
        runTest {
            val playwrightClient = PlaywrightMCPClient("http://localhost:3000")
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
            }
        }
}
