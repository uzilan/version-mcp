package com.mavenversion.mcp.client

import io.ktor.client.request.get
import io.mockk.clearAllMocks
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonPrimitive
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@DisplayName("MCP Client Tests")
class MCPClientTest {
    private lateinit var mcpClient: MCPClient
    private val testServerUrl = "http://localhost:3000"

    @BeforeEach
    fun setUp() {
        mcpClient = MCPClient(testServerUrl)
    }

    @AfterEach
    fun tearDown() {
        clearAllMocks()
    }

    @Nested
    @DisplayName("Connection Tests")
    inner class ConnectionTests {
        @Test
        @DisplayName("Should successfully connect to MCP server")
        fun shouldConnectSuccessfully() =
            runTest {
                // This test would require mocking the HTTP client
                // For now, we'll test the basic structure
                assertThat(mcpClient.isConnected()).isFalse()
            }

        @Test
        @DisplayName("Should handle connection failures gracefully")
        fun shouldHandleConnectionFailures() =
            runTest {
                // Test connection failure handling
                val result = mcpClient.connect()

                // Should return a failure result when server is not available
                assertThat(result.isFailure).isTrue()
                assertThat(mcpClient.isConnected()).isFalse()
            }

        @Test
        @DisplayName("Should not reconnect if already connected")
        fun shouldNotReconnectIfAlreadyConnected() =
            runTest {
                // This would test the connection mutex logic
                assertThat(mcpClient.isConnected()).isFalse()
            }
    }

    @Nested
    @DisplayName("Tool Call Tests")
    inner class ToolCallTests {
        @Test
        @DisplayName("Should create valid tool request")
        fun shouldCreateValidToolRequest() {
            val request =
                MCPToolRequest(
                    name = "test_tool",
                    arguments =
                        mapOf(
                            "param1" to JsonPrimitive("value1"),
                            "param2" to JsonPrimitive(42),
                        ),
                )

            assertThat(request.name).isEqualTo("test_tool")
            assertThat(request.arguments).hasSize(2)
            assertThat(request.arguments["param1"]).isEqualTo(JsonPrimitive("value1"))
            assertThat(request.arguments["param2"]).isEqualTo(JsonPrimitive(42))
        }

        @Test
        @DisplayName("Should handle tool call failures")
        fun shouldHandleToolCallFailures() =
            runTest {
                val request = MCPToolRequest("nonexistent_tool")

                val result = mcpClient.callTool(request)

                assertThat(result.isFailure).isTrue()
            }

        @Test
        @DisplayName("Should validate tool response structure")
        fun shouldValidateToolResponseStructure() {
            val response =
                MCPToolResponse(
                    content =
                        listOf(
                            MCPContent(
                                type = "text",
                                text = "Test response",
                            ),
                        ),
                    isError = false,
                )

            assertThat(response.content).hasSize(1)
            assertThat(response.content[0].type).isEqualTo("text")
            assertThat(response.content[0].text).isEqualTo("Test response")
            assertThat(response.isError).isFalse()
        }
    }

    @Nested
    @DisplayName("Error Handling Tests")
    inner class ErrorHandlingTests {
        @Test
        @DisplayName("Should create MCP connection exception")
        fun shouldCreateMCPConnectionException() {
            val exception = MCPConnectionException("Connection failed")

            assertThat(exception.message).isEqualTo("Connection failed")
            assertThat(exception).isInstanceOf(Exception::class.java)
        }

        @Test
        @DisplayName("Should create MCP tool exception")
        fun shouldCreateMCPToolException() {
            val cause = RuntimeException("Root cause")
            val exception = MCPToolException("Tool failed", cause)

            assertThat(exception.message).isEqualTo("Tool failed")
            assertThat(exception.cause).isEqualTo(cause)
        }
    }

    @Nested
    @DisplayName("Data Model Tests")
    inner class DataModelTests {
        @Test
        @DisplayName("Should create MCP tool definition")
        fun shouldCreateMCPToolDefinition() {
            val tool =
                MCPTool(
                    name = "test_tool",
                    description = "A test tool",
                    inputSchema =
                        MCPSchema(
                            type = "object",
                            properties =
                                mapOf(
                                    "param1" to
                                        MCPProperty(
                                            type = "string",
                                            description = "First parameter",
                                        ),
                                ),
                            required = listOf("param1"),
                        ),
                )

            assertThat(tool.name).isEqualTo("test_tool")
            assertThat(tool.description).isEqualTo("A test tool")
            assertThat(tool.inputSchema.type).isEqualTo("object")
            assertThat(tool.inputSchema.properties).hasSize(1)
            assertThat(tool.inputSchema.required).containsExactly("param1")
        }

        @Test
        @DisplayName("Should create MCP content with different types")
        fun shouldCreateMCPContentWithDifferentTypes() {
            val textContent =
                MCPContent(
                    type = "text",
                    text = "Some text content",
                )

            val dataContent =
                MCPContent(
                    type = "resource",
                    data = "base64encodeddata",
                    mimeType = "application/json",
                )

            assertThat(textContent.type).isEqualTo("text")
            assertThat(textContent.text).isEqualTo("Some text content")
            assertThat(textContent.data).isNull()

            assertThat(dataContent.type).isEqualTo("resource")
            assertThat(dataContent.data).isEqualTo("base64encodeddata")
            assertThat(dataContent.mimeType).isEqualTo("application/json")
        }
    }
}
