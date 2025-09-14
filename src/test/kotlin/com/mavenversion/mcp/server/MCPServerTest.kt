package com.mavenversion.mcp.server

import com.mavenversion.mcp.client.MCPToolRequest
import com.mavenversion.mcp.client.MCPToolResponse
import com.mavenversion.mcp.client.MCPToolsListResponse
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class MCPServerTest {
    private lateinit var mockToolRegistry: ToolRegistry
    private lateinit var mcpServer: MCPServer

    @BeforeEach
    fun setUp() {
        mockToolRegistry = mockk<ToolRegistry>(relaxed = true)
        mcpServer = MCPServer(mockToolRegistry)
    }

    @Nested
    @DisplayName("Server Lifecycle Tests")
    inner class ServerLifecycleTests {
        @Test
        fun `Should start server successfully`() {
            // Given
            every { mockToolRegistry.getToolCount() } returns 5

            // When
            mcpServer.start()

            // Then
            assertThat(mcpServer.isServerRunning()).isTrue
            verify { mockToolRegistry.getToolCount() }
        }

        @Test
        fun `Should not start server if already running`() {
            // Given
            mcpServer.start()

            // When
            mcpServer.start()

            // Then
            assertThat(mcpServer.isServerRunning()).isTrue
        }

        @Test
        fun `Should stop server successfully`() {
            // Given
            mcpServer.start()
            assertThat(mcpServer.isServerRunning()).isTrue

            // When
            mcpServer.stop()

            // Then
            assertThat(mcpServer.isServerRunning()).isFalse
        }

        @Test
        fun `Should not stop server if not running`() {
            // When
            mcpServer.stop()

            // Then
            assertThat(mcpServer.isServerRunning()).isFalse
        }

        @Test
        fun `Should shutdown gracefully`() {
            // Given
            mcpServer.start()

            // When
            mcpServer.shutdown()

            // Then
            assertThat(mcpServer.isServerRunning()).isFalse
        }
    }

    @Nested
    @DisplayName("Tool Execution Tests")
    inner class ToolExecutionTests {
        @Test
        fun `Should handle tool execution request`() =
            runTest {
                // Given
                val toolName = "test-tool"
                val request = MCPToolRequest(toolName, emptyMap())
                val expectedResponse =
                    MCPToolResponse(
                        content = listOf(),
                        isError = false,
                    )

                coEvery { mockToolRegistry.executeTool(toolName, request) } returns expectedResponse

                // When
                val response = mcpServer.handleToolExecution(toolName, request)

                // Then
                assertThat(response).isEqualTo(expectedResponse)
                coVerify { mockToolRegistry.executeTool(toolName, request) }
            }
    }

    @Nested
    @DisplayName("Tools List Tests")
    inner class ToolsListTests {
        @Test
        fun `Should handle tools list request`() {
            // Given
            val mockTools =
                listOf(
                    createMockTool("tool-1"),
                    createMockTool("tool-2"),
                )
            val expectedResponse = MCPToolsListResponse(tools = mockTools)

            every { mockToolRegistry.getAllTools() } returns mockTools

            // When
            val response = mcpServer.handleToolsListRequest()

            // Then
            assertThat(response).isEqualTo(expectedResponse)
            verify { mockToolRegistry.getAllTools() }
        }
    }

    @Nested
    @DisplayName("Initialization Tests")
    inner class InitializationTests {
        @Test
        fun `Should handle initialization request`() {
            // When
            val response = mcpServer.handleInitialize()

            // Then
            assertThat(response).containsKey("protocolVersion")
            assertThat(response).containsKey("capabilities")
            assertThat(response).containsKey("serverInfo")

            val serverInfo = response["serverInfo"] as Map<String, Any>
            assertThat(serverInfo["name"]).isEqualTo("maven-version-mcp-server")
            assertThat(serverInfo["version"]).isEqualTo("1.0.0")

            val capabilities = response["capabilities"] as Map<String, Any>
            assertThat(capabilities).containsKey("tools")
        }
    }

    private fun createMockTool(name: String): com.mavenversion.mcp.client.MCPTool {
        return com.mavenversion.mcp.client.MCPTool(
            name = name,
            description = "Test tool $name",
            inputSchema =
                com.mavenversion.mcp.client.MCPSchema(
                    type = "object",
                    properties = emptyMap(),
                    required = emptyList(),
                ),
        )
    }
}
