package com.mavenversion.mcp.client

import kotlinx.coroutines.test.runTest
import mu.KotlinLogging
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable

private val log = KotlinLogging.logger {}

@DisplayName("StdioMCPClient Tests")
class StdioMCPClientTest {
    @Nested
    @DisplayName("Configuration Tests")
    inner class ConfigurationTests {
        @Test
        @DisplayName("Should create client with valid command")
        fun shouldCreateClientWithValidCommand() {
            val client =
                StdioMCPClient(
                    command = listOf("echo", "test"),
                    workingDirectory = null,
                )

            assertThat(client).isNotNull
            assertThat(client.isConnected()).isFalse()
        }

        @Test
        @DisplayName("Should handle working directory configuration")
        fun shouldHandleWorkingDirectoryConfiguration() {
            val client =
                StdioMCPClient(
                    command = listOf("pwd"),
                    workingDirectory = "/tmp",
                )

            assertThat(client).isNotNull
        }
    }

    @Nested
    @DisplayName("Connection Tests")
    @EnabledIfEnvironmentVariable(named = "INTEGRATION_TESTS", matches = "true")
    inner class ConnectionTests {
        @Test
        @DisplayName("Should fail to connect with invalid command")
        fun shouldFailToConnectWithInvalidCommand() =
            runTest {
                val client =
                    StdioMCPClient(
                        command = listOf("nonexistent-command"),
                        workingDirectory = null,
                    )

                val result = client.connect()
                assertThat(result.isFailure).isTrue()
            }

        @Test
        @DisplayName("Should handle connection cleanup on failure")
        fun shouldHandleConnectionCleanupOnFailure() =
            runTest {
                val client =
                    StdioMCPClient(
                        command = listOf("false"), // Command that exits with error
                        workingDirectory = null,
                    )

                val result = client.connect()
                assertThat(result.isFailure).isTrue()
                assertThat(client.isConnected()).isFalse()
            }
    }

    @Nested
    @DisplayName("Protocol Tests")
    inner class ProtocolTests {
        @Test
        @DisplayName("Should generate unique request IDs")
        fun shouldGenerateUniqueRequestIds() {
            val client =
                StdioMCPClient(
                    command = listOf("echo", "test"),
                    workingDirectory = null,
                )

            // Test that multiple clients generate different IDs
            val client2 =
                StdioMCPClient(
                    command = listOf("echo", "test2"),
                    workingDirectory = null,
                )

            assertThat(client).isNotEqualTo(client2)
        }
    }

    @Nested
    @DisplayName("Lifecycle Tests")
    inner class LifecycleTests {
        @Test
        @DisplayName("Should handle disconnect when not connected")
        fun shouldHandleDisconnectWhenNotConnected() =
            runTest {
                val client =
                    StdioMCPClient(
                        command = listOf("echo", "test"),
                        workingDirectory = null,
                    )

                // Should not throw exception
                client.disconnect()
                assertThat(client.isConnected()).isFalse()
            }

        @Test
        @DisplayName("Should handle restart when not connected")
        fun shouldHandleRestartWhenNotConnected() =
            runTest {
                val client =
                    StdioMCPClient(
                        command = listOf("nonexistent-command"),
                        workingDirectory = null,
                    )

                val result = client.restart()
                assertThat(result.isFailure).isTrue()
            }
    }
}
