package com.mavenversion.mcp.server

import com.mavenversion.mcp.client.MCPContent
import com.mavenversion.mcp.client.MCPProperty
import com.mavenversion.mcp.client.MCPSchema
import com.mavenversion.mcp.client.MCPTool
import com.mavenversion.mcp.client.MCPToolRequest
import com.mavenversion.mcp.client.MCPToolResponse
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class ToolRegistryTest {
    private lateinit var toolRegistry: ToolRegistry
    private lateinit var mockTool: MCPToolInterface

    @BeforeEach
    fun setUp() {
        toolRegistry = ToolRegistry()
        mockTool = mockk<MCPToolInterface>()
    }

    @Nested
    @DisplayName("Tool Registration Tests")
    inner class ToolRegistrationTests {
        @Test
        fun `Should register a tool successfully`() {
            // Given
            val toolDefinition = createTestToolDefinition("test-tool")
            coEvery { mockTool.getToolDefinition() } returns toolDefinition

            // When
            toolRegistry.registerTool(mockTool)

            // Then
            assertThat(toolRegistry.hasTool("test-tool")).isTrue
            assertThat(toolRegistry.getToolCount()).isEqualTo(1)
        }

        @Test
        fun `Should register multiple tools`() {
            // Given
            val tool1 = mockk<MCPToolInterface>()
            val tool2 = mockk<MCPToolInterface>()
            val definition1 = createTestToolDefinition("tool-1")
            val definition2 = createTestToolDefinition("tool-2")

            coEvery { tool1.getToolDefinition() } returns definition1
            coEvery { tool2.getToolDefinition() } returns definition2

            // When
            toolRegistry.registerTool(tool1)
            toolRegistry.registerTool(tool2)

            // Then
            assertThat(toolRegistry.getToolCount()).isEqualTo(2)
            assertThat(toolRegistry.hasTool("tool-1")).isTrue
            assertThat(toolRegistry.hasTool("tool-2")).isTrue
        }

        @Test
        fun `Should overwrite existing tool with same name`() {
            // Given
            val tool1 = mockk<MCPToolInterface>()
            val tool2 = mockk<MCPToolInterface>()
            val definition1 = createTestToolDefinition("same-tool")
            val definition2 = createTestToolDefinition("same-tool")

            coEvery { tool1.getToolDefinition() } returns definition1
            coEvery { tool2.getToolDefinition() } returns definition2

            // When
            toolRegistry.registerTool(tool1)
            toolRegistry.registerTool(tool2)

            // Then
            assertThat(toolRegistry.getToolCount()).isEqualTo(1)
            assertThat(toolRegistry.getTool("same-tool")).isEqualTo(tool2)
        }
    }

    @Nested
    @DisplayName("Tool Retrieval Tests")
    inner class ToolRetrievalTests {
        @Test
        fun `Should get all registered tools`() {
            // Given
            val tool1 = mockk<MCPToolInterface>()
            val tool2 = mockk<MCPToolInterface>()
            val definition1 = createTestToolDefinition("tool-1")
            val definition2 = createTestToolDefinition("tool-2")

            coEvery { tool1.getToolDefinition() } returns definition1
            coEvery { tool2.getToolDefinition() } returns definition2

            toolRegistry.registerTool(tool1)
            toolRegistry.registerTool(tool2)

            // When
            val allTools = toolRegistry.getAllTools()

            // Then
            assertThat(allTools).hasSize(2)
            assertThat(allTools.map { it.name }).containsExactlyInAnyOrder("tool-1", "tool-2")
        }

        @Test
        fun `Should get specific tool by name`() {
            // Given
            val toolDefinition = createTestToolDefinition("specific-tool")
            coEvery { mockTool.getToolDefinition() } returns toolDefinition
            toolRegistry.registerTool(mockTool)

            // When
            val retrievedTool = toolRegistry.getTool("specific-tool")

            // Then
            assertThat(retrievedTool).isEqualTo(mockTool)
        }

        @Test
        fun `Should return null for non-existent tool`() {
            // When
            val retrievedTool = toolRegistry.getTool("non-existent")

            // Then
            assertThat(retrievedTool).isNull()
        }
    }

    @Nested
    @DisplayName("Tool Execution Tests")
    inner class ToolExecutionTests {
        @Test
        fun `Should execute registered tool successfully`() =
            runTest {
                // Given
                val toolDefinition = createTestToolDefinition("executable-tool")
                val request = MCPToolRequest("executable-tool", emptyMap())
                val expectedResponse =
                    MCPToolResponse(
                        content =
                            listOf(
                                MCPContent(
                                    type = "text",
                                    text = "Tool executed successfully",
                                ),
                            ),
                    )

                coEvery { mockTool.getToolDefinition() } returns toolDefinition
                coEvery { mockTool.execute(request) } returns expectedResponse

                toolRegistry.registerTool(mockTool)

                // When
                val response = toolRegistry.executeTool("executable-tool", request)

                // Then
                assertThat(response).isEqualTo(expectedResponse)
            }

        @Test
        fun `Should return error response for non-existent tool`() =
            runTest {
                // Given
                val request = MCPToolRequest("non-existent", emptyMap())

                // When
                val response = toolRegistry.executeTool("non-existent", request)

                // Then
                assertThat(response.isError).isTrue
                assertThat(response.content).hasSize(1)
                assertThat(response.content[0].text).contains("Tool 'non-existent' not found")
            }
    }

    @Nested
    @DisplayName("Registry Management Tests")
    inner class RegistryManagementTests {
        @Test
        fun `Should clear all tools`() {
            // Given
            val tool1 = mockk<MCPToolInterface>()
            val tool2 = mockk<MCPToolInterface>()
            val definition1 = createTestToolDefinition("tool-1")
            val definition2 = createTestToolDefinition("tool-2")

            coEvery { tool1.getToolDefinition() } returns definition1
            coEvery { tool2.getToolDefinition() } returns definition2

            toolRegistry.registerTool(tool1)
            toolRegistry.registerTool(tool2)

            // When
            toolRegistry.clear()

            // Then
            assertThat(toolRegistry.getToolCount()).isEqualTo(0)
            assertThat(toolRegistry.getAllTools()).isEmpty()
        }

        @Test
        fun `Should return correct tool count`() {
            // Given
            val tool1 = mockk<MCPToolInterface>()
            val tool2 = mockk<MCPToolInterface>()
            val tool3 = mockk<MCPToolInterface>()
            val definition1 = createTestToolDefinition("tool-1")
            val definition2 = createTestToolDefinition("tool-2")
            val definition3 = createTestToolDefinition("tool-3")

            coEvery { tool1.getToolDefinition() } returns definition1
            coEvery { tool2.getToolDefinition() } returns definition2
            coEvery { tool3.getToolDefinition() } returns definition3

            // When
            toolRegistry.registerTool(tool1)
            assertThat(toolRegistry.getToolCount()).isEqualTo(1)

            toolRegistry.registerTool(tool2)
            assertThat(toolRegistry.getToolCount()).isEqualTo(2)

            toolRegistry.registerTool(tool3)
            assertThat(toolRegistry.getToolCount()).isEqualTo(3)
        }
    }

    private fun createTestToolDefinition(name: String): MCPTool {
        return MCPTool(
            name = name,
            description = "Test tool for $name",
            inputSchema =
                MCPSchema(
                    type = "object",
                    properties =
                        mapOf(
                            "param" to
                                MCPProperty(
                                    type = "string",
                                    description = "Test parameter",
                                ),
                        ),
                    required = listOf("param"),
                ),
        )
    }
}
