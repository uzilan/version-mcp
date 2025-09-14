package com.mavenversion.mcp.tools

import com.mavenversion.mcp.client.MCPToolRequest
import com.mavenversion.mcp.models.Version
import com.mavenversion.mcp.web.MavenRepositoryClient
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonPrimitive
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@DisplayName("GetLatestVersionTool Tests")
class GetLatestVersionToolTest {
    private lateinit var mockMavenRepositoryClient: MavenRepositoryClient
    private lateinit var getLatestVersionTool: GetLatestVersionTool

    @BeforeEach
    fun setUp() {
        mockMavenRepositoryClient = mockk()
        getLatestVersionTool = GetLatestVersionTool(mockMavenRepositoryClient, mockk(), mockk())
    }

    @Nested
    @DisplayName("Tool Definition")
    inner class ToolDefinitionTests {
        @Test
        @DisplayName("Should return correct tool definition")
        fun shouldReturnCorrectToolDefinition() {
            val toolDefinition = getLatestVersionTool.getToolDefinition()

            assertThat(toolDefinition.name).isEqualTo("get_latest_version")
            assertThat(toolDefinition.description).contains("Get the latest version")
            assertThat(toolDefinition.inputSchema.type).isEqualTo("object")
            assertThat(toolDefinition.inputSchema.required).containsExactly("groupId", "artifactId")
            assertThat(toolDefinition.inputSchema.properties).containsKeys("groupId", "artifactId")
        }
    }

    @Nested
    @DisplayName("Parameter Validation")
    inner class ParameterValidationTests {
        @Test
        @DisplayName("Should fail when groupId parameter is missing")
        fun shouldFailWhenGroupIdParameterIsMissing() =
            runTest {
                val request =
                    MCPToolRequest(
                        name = "get_latest_version",
                        arguments = mapOf("artifactId" to JsonPrimitive("spring-core")),
                    )

                val response = getLatestVersionTool.execute(request)

                assertThat(response.isError).isTrue()
                assertThat(response.content.first().text).contains("Missing required parameter: groupId")
            }

        @Test
        @DisplayName("Should fail when artifactId parameter is missing")
        fun shouldFailWhenArtifactIdParameterIsMissing() =
            runTest {
                val request =
                    MCPToolRequest(
                        name = "get_latest_version",
                        arguments = mapOf("groupId" to JsonPrimitive("org.springframework")),
                    )

                val response = getLatestVersionTool.execute(request)

                assertThat(response.isError).isTrue()
                assertThat(response.content.first().text).contains("Missing required parameter: artifactId")
            }

        @Test
        @DisplayName("Should fail when groupId is empty")
        fun shouldFailWhenGroupIdIsEmpty() =
            runTest {
                val request =
                    MCPToolRequest(
                        name = "get_latest_version",
                        arguments =
                            mapOf(
                                "groupId" to JsonPrimitive(""),
                                "artifactId" to JsonPrimitive("spring-core"),
                            ),
                    )

                val response = getLatestVersionTool.execute(request)

                assertThat(response.isError).isTrue()
                assertThat(response.content.first().text).contains("Group ID cannot be empty")
            }

        @Test
        @DisplayName("Should fail when artifactId is empty")
        fun shouldFailWhenArtifactIdIsEmpty() =
            runTest {
                val request =
                    MCPToolRequest(
                        name = "get_latest_version",
                        arguments =
                            mapOf(
                                "groupId" to JsonPrimitive("org.springframework"),
                                "artifactId" to JsonPrimitive(""),
                            ),
                    )

                val response = getLatestVersionTool.execute(request)

                assertThat(response.isError).isTrue()
                assertThat(response.content.first().text).contains("Artifact ID cannot be empty")
            }

        @Test
        @DisplayName("Should fail when groupId is too short")
        fun shouldFailWhenGroupIdIsTooShort() =
            runTest {
                val request =
                    MCPToolRequest(
                        name = "get_latest_version",
                        arguments =
                            mapOf(
                                "groupId" to JsonPrimitive("a"),
                                "artifactId" to JsonPrimitive("spring-core"),
                            ),
                    )

                val response = getLatestVersionTool.execute(request)

                assertThat(response.isError).isTrue()
                assertThat(response.content.first().text).contains("Group ID must be at least 2 characters long")
            }

        @Test
        @DisplayName("Should fail when artifactId is too short")
        fun shouldFailWhenArtifactIdIsTooShort() =
            runTest {
                val request =
                    MCPToolRequest(
                        name = "get_latest_version",
                        arguments =
                            mapOf(
                                "groupId" to JsonPrimitive("org.springframework"),
                                "artifactId" to JsonPrimitive("a"),
                            ),
                    )

                val response = getLatestVersionTool.execute(request)

                assertThat(response.isError).isTrue()
                assertThat(response.content.first().text).contains("Artifact ID must be at least 2 characters long")
            }
    }

    @Nested
    @DisplayName("Successful Execution")
    inner class SuccessfulExecutionTests {
        @Test
        @DisplayName("Should get latest version successfully")
        fun shouldGetLatestVersionSuccessfully() =
            runTest {
                val latestVersion =
                    Version(
                        version = "6.1.0",
                        releaseDate = "2024-01-15",
                        isLatest = true,
                        downloads = 1500000L,
                        vulnerabilities = 0,
                    )

                coEvery {
                    mockMavenRepositoryClient.getLatestVersion("org.springframework", "spring-core")
                } returns Result.success(latestVersion)

                val request =
                    MCPToolRequest(
                        name = "get_latest_version",
                        arguments =
                            mapOf(
                                "groupId" to JsonPrimitive("org.springframework"),
                                "artifactId" to JsonPrimitive("spring-core"),
                            ),
                    )

                val response = getLatestVersionTool.execute(request)

                assertThat(response.isError).isFalse()
                assertThat(response.content.first().text).contains("Latest version for org.springframework:spring-core")
                assertThat(response.content.first().text).contains("Version: 6.1.0")
                assertThat(response.content.first().text).contains("Release Date: 2024-01-15")
                assertThat(response.content.first().text).contains("Downloads: 1500000")
            }

        @Test
        @DisplayName("Should handle version with vulnerabilities")
        fun shouldHandleVersionWithVulnerabilities() =
            runTest {
                val latestVersion =
                    Version(
                        version = "5.3.21",
                        releaseDate = "2023-12-01",
                        isLatest = true,
                        downloads = 2000000L,
                        vulnerabilities = 2,
                    )

                coEvery {
                    mockMavenRepositoryClient.getLatestVersion("org.springframework", "spring-core")
                } returns Result.success(latestVersion)

                val request =
                    MCPToolRequest(
                        name = "get_latest_version",
                        arguments =
                            mapOf(
                                "groupId" to JsonPrimitive("org.springframework"),
                                "artifactId" to JsonPrimitive("spring-core"),
                            ),
                    )

                val response = getLatestVersionTool.execute(request)

                assertThat(response.isError).isFalse()
                assertThat(response.content.first().text).contains("⚠️  Vulnerabilities: 2")
            }

        @Test
        @DisplayName("Should handle version without optional fields")
        fun shouldHandleVersionWithoutOptionalFields() =
            runTest {
                val latestVersion =
                    Version(
                        version = "1.0.0",
                        isLatest = true,
                    )

                coEvery {
                    mockMavenRepositoryClient.getLatestVersion("com.example", "test-lib")
                } returns Result.success(latestVersion)

                val request =
                    MCPToolRequest(
                        name = "get_latest_version",
                        arguments =
                            mapOf(
                                "groupId" to JsonPrimitive("com.example"),
                                "artifactId" to JsonPrimitive("test-lib"),
                            ),
                    )

                val response = getLatestVersionTool.execute(request)

                assertThat(response.isError).isFalse()
                assertThat(response.content.first().text).contains("Latest version for com.example:test-lib")
                assertThat(response.content.first().text).contains("Version: 1.0.0")
                assertThat(response.content.first().text).doesNotContain("Release Date:")
                assertThat(response.content.first().text).doesNotContain("Downloads:")
            }
    }

    @Nested
    @DisplayName("Error Handling")
    inner class ErrorHandlingTests {
        @Test
        @DisplayName("Should handle dependency not found")
        fun shouldHandleDependencyNotFound() =
            runTest {
                coEvery {
                    mockMavenRepositoryClient.getLatestVersion("nonexistent", "dependency")
                } returns Result.success(null)

                val request =
                    MCPToolRequest(
                        name = "get_latest_version",
                        arguments =
                            mapOf(
                                "groupId" to JsonPrimitive("nonexistent"),
                                "artifactId" to JsonPrimitive("dependency"),
                            ),
                    )

                val response = getLatestVersionTool.execute(request)

                assertThat(response.isError).isTrue()
                assertThat(response.content.first().text).contains("Dependency not found: nonexistent:dependency")
            }

        @Test
        @DisplayName("Should handle repository client errors")
        fun shouldHandleRepositoryClientErrors() =
            runTest {
                coEvery {
                    mockMavenRepositoryClient.getLatestVersion("org.springframework", "spring-core")
                } returns Result.failure(RuntimeException("Network error"))

                val request =
                    MCPToolRequest(
                        name = "get_latest_version",
                        arguments =
                            mapOf(
                                "groupId" to JsonPrimitive("org.springframework"),
                                "artifactId" to JsonPrimitive("spring-core"),
                            ),
                    )

                val response = getLatestVersionTool.execute(request)

                assertThat(response.isError).isTrue()
                assertThat(response.content.first().text).contains("Failed to get latest version: Network error")
            }
    }
}
