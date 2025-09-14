package com.mavenversion.mcp.client

import kotlinx.coroutines.test.runTest
import mu.KotlinLogging
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable

private val log = KotlinLogging.logger {}

@DisplayName("MCPProcessManager Tests")
class MCPProcessManagerTest {
    @Nested
    @DisplayName("Configuration Tests")
    inner class ConfigurationTests {
        @Test
        @DisplayName("Should create process manager")
        fun shouldCreateProcessManager() {
            val manager = MCPProcessManager()
            assertThat(manager).isNotNull

            val status = manager.getStatus()
            assertThat(status).isEmpty()
        }

        @Test
        @DisplayName("Should handle server configuration")
        fun shouldHandleServerConfiguration() {
            val config =
                MCPServerConfig(
                    name = "test-server",
                    command = listOf("echo", "test"),
                    args = listOf("arg1", "arg2"),
                    env = mapOf("TEST_VAR" to "test_value"),
                )

            assertThat(config.name).isEqualTo("test-server")
            assertThat(config.getFullCommand()).containsExactly("echo", "test", "arg1", "arg2")
            assertThat(config.env).containsEntry("TEST_VAR", "test_value")
        }
    }

    @Nested
    @DisplayName("Default Configuration Tests")
    inner class DefaultConfigurationTests {
        @Test
        @DisplayName("Should provide Playwright default configuration")
        fun shouldProvidePlaywrightDefaultConfiguration() {
            val config = MCPServerConfig.playwrightDefault()

            assertThat(config.name).isEqualTo("playwright")
            assertThat(config.command).containsExactly("npx", "@playwright/mcp")
            assertThat(config.env).containsEntry("NODE_ENV", "production")
        }

        @Test
        @DisplayName("Should provide Playwright NPX configuration")
        fun shouldProvidePlaywrightNpxConfiguration() {
            val config = MCPServerConfig.playwrightNpx()

            assertThat(config.name).isEqualTo("playwright")
            assertThat(config.command).containsExactly("npx", "@playwright/mcp")
            assertThat(config.env).containsEntry("NODE_ENV", "production")
        }
    }

    @Nested
    @DisplayName("Process Management Tests")
    @EnabledIfEnvironmentVariable(named = "INTEGRATION_TESTS", matches = "true")
    inner class ProcessManagementTests {
        @Test
        @DisplayName("Should fail to get client with invalid command")
        fun shouldFailToGetClientWithInvalidCommand() =
            runTest {
                val manager = MCPProcessManager()
                val config =
                    MCPServerConfig(
                        name = "invalid-server",
                        command = listOf("nonexistent-command"),
                        autoRestart = false,
                    )

                val result = manager.getClient(config)
                assertThat(result.isFailure).isTrue()

                val status = manager.getStatus()
                assertThat(status).doesNotContainKey("invalid-server")
            }

        @Test
        @DisplayName("Should handle server restart failure")
        fun shouldHandleServerRestartFailure() =
            runTest {
                val manager = MCPProcessManager()

                val result = manager.restartServer("nonexistent-server")
                assertThat(result.isFailure).isTrue()
            }

        @Test
        @DisplayName("Should handle server stop for nonexistent server")
        fun shouldHandleServerStopForNonexistentServer() =
            runTest {
                val manager = MCPProcessManager()

                val result = manager.stopServer("nonexistent-server")
                assertThat(result.isFailure).isTrue()
            }

        @Test
        @DisplayName("Should handle stop all with no servers")
        fun shouldHandleStopAllWithNoServers() =
            runTest {
                val manager = MCPProcessManager()

                // Should not throw exception
                manager.stopAll()

                val status = manager.getStatus()
                assertThat(status).isEmpty()
            }

        @Test
        @DisplayName("Should handle health check for nonexistent server")
        fun shouldHandleHealthCheckForNonexistentServer() =
            runTest {
                val manager = MCPProcessManager()

                val result = manager.healthCheck("nonexistent-server")
                assertThat(result.getOrNull()).isFalse()
            }
    }

    @Nested
    @DisplayName("Status Tests")
    inner class StatusTests {
        @Test
        @DisplayName("Should provide server status information")
        fun shouldProvideServerStatusInformation() {
            val status =
                MCPServerStatus(
                    name = "test-server",
                    isConnected = true,
                    restartAttempts = 2,
                    maxRestartAttempts = 3,
                )

            assertThat(status.name).isEqualTo("test-server")
            assertThat(status.isConnected).isTrue()
            assertThat(status.restartAttempts).isEqualTo(2)
            assertThat(status.maxRestartAttempts).isEqualTo(3)
        }
    }
}
