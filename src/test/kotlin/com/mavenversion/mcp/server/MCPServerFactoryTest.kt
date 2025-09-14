package com.mavenversion.mcp.server

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class MCPServerFactoryTest {
    private val serverFactory = MCPServerFactory()

    @Nested
    @DisplayName("Server Creation Tests")
    inner class ServerCreationTests {
        @Test
        @DisplayName("Should create a fully configured server")
        fun shouldCreateFullyConfiguredServer() {
            // When
            val server = serverFactory.createServer()

            // Then
            assertThat(server).isNotNull
            assertThat(server).isInstanceOf(MCPServer::class.java)
        }

        @Test
        @DisplayName("Should create a test server")
        fun shouldCreateTestServer() {
            // When
            val server = serverFactory.createTestServer()

            // Then
            assertThat(server).isNotNull
            assertThat(server).isInstanceOf(MCPServer::class.java)
        }

        @Test
        @DisplayName("Should create different server instances")
        fun shouldCreateDifferentServerInstances() {
            // When
            val server1 = serverFactory.createServer()
            val server2 = serverFactory.createServer()

            // Then
            assertThat(server1).isNotSameAs(server2)
        }
    }
}
