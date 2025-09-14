package com.mavenversion.mcp.tools

import com.mavenversion.mcp.client.MCPToolRequest
import com.mavenversion.mcp.models.Dependency
import com.mavenversion.mcp.models.SearchResult
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

@DisplayName("SearchDependencyTool Tests")
class SearchDependencyToolTest {
    private lateinit var mockMavenRepositoryClient: MavenRepositoryClient
    private lateinit var searchDependencyTool: SearchDependencyTool

    @BeforeEach
    fun setUp() {
        mockMavenRepositoryClient = mockk()
        searchDependencyTool = SearchDependencyTool(mockMavenRepositoryClient, mockk(), mockk())
    }

    @Nested
    @DisplayName("Tool Definition")
    inner class ToolDefinitionTests {
        @Test
        @DisplayName("Should return correct tool definition")
        fun shouldReturnCorrectToolDefinition() {
            val toolDefinition = searchDependencyTool.getToolDefinition()

            assertThat(toolDefinition.name).isEqualTo("search_dependency")
            assertThat(toolDefinition.description).contains("Search for Java dependencies")
            assertThat(toolDefinition.inputSchema.type).isEqualTo("object")
            assertThat(toolDefinition.inputSchema.required).containsExactly("query")
            assertThat(toolDefinition.inputSchema.properties).containsKeys("query", "limit")
        }
    }

    @Nested
    @DisplayName("Parameter Validation")
    inner class ParameterValidationTests {
        @Test
        @DisplayName("Should fail when query parameter is missing")
        fun shouldFailWhenQueryParameterIsMissing() =
            runTest {
                val request =
                    MCPToolRequest(
                        name = "search_dependency",
                        arguments = emptyMap(),
                    )

                val response = searchDependencyTool.execute(request)

                assertThat(response.isError).isTrue()
                assertThat(response.content.first().text).contains("Missing required parameter: query")
            }

        @Test
        @DisplayName("Should fail when query is empty")
        fun shouldFailWhenQueryIsEmpty() =
            runTest {
                val request =
                    MCPToolRequest(
                        name = "search_dependency",
                        arguments = mapOf("query" to JsonPrimitive("")),
                    )

                val response = searchDependencyTool.execute(request)

                assertThat(response.isError).isTrue()
                assertThat(response.content.first().text).contains("Search query cannot be empty")
            }

        @Test
        @DisplayName("Should fail when query is too short")
        fun shouldFailWhenQueryIsTooShort() =
            runTest {
                val request =
                    MCPToolRequest(
                        name = "search_dependency",
                        arguments = mapOf("query" to JsonPrimitive("a")),
                    )

                val response = searchDependencyTool.execute(request)

                assertThat(response.isError).isTrue()
                assertThat(response.content.first().text).contains("Search query must be at least 2 characters long")
            }

        @Test
        @DisplayName("Should fail when limit is invalid")
        fun shouldFailWhenLimitIsInvalid() =
            runTest {
                val request =
                    MCPToolRequest(
                        name = "search_dependency",
                        arguments =
                            mapOf(
                                "query" to JsonPrimitive("spring"),
                                "limit" to JsonPrimitive("0"),
                            ),
                    )

                val response = searchDependencyTool.execute(request)

                assertThat(response.isError).isTrue()
                assertThat(response.content.first().text).contains("Limit must be between 1 and 100")
            }

        @Test
        @DisplayName("Should fail when limit is too high")
        fun shouldFailWhenLimitIsTooHigh() =
            runTest {
                val request =
                    MCPToolRequest(
                        name = "search_dependency",
                        arguments =
                            mapOf(
                                "query" to JsonPrimitive("spring"),
                                "limit" to JsonPrimitive("101"),
                            ),
                    )

                val response = searchDependencyTool.execute(request)

                assertThat(response.isError).isTrue()
                assertThat(response.content.first().text).contains("Limit must be between 1 and 100")
            }
    }

    @Nested
    @DisplayName("Successful Execution")
    inner class SuccessfulExecutionTests {
        @Test
        @DisplayName("Should execute search successfully with default limit")
        fun shouldExecuteSearchSuccessfullyWithDefaultLimit() =
            runTest {
                val dependencies =
                    listOf(
                        Dependency(
                            groupId = "org.springframework",
                            artifactId = "spring-core",
                            description = "Spring Framework Core",
                            url = "https://mvnrepository.com/artifact/org.springframework/spring-core",
                        ),
                        Dependency(
                            groupId = "org.springframework.boot",
                            artifactId = "spring-boot-starter",
                            description = "Spring Boot Starter",
                            url = "https://mvnrepository.com/artifact/org.springframework.boot/spring-boot-starter",
                        ),
                    )

                val searchResult =
                    SearchResult(
                        dependencies = dependencies,
                        totalResults = dependencies.size,
                        query = "spring",
                    )

                coEvery { mockMavenRepositoryClient.searchDependencies("spring") } returns Result.success(searchResult)

                val request =
                    MCPToolRequest(
                        name = "search_dependency",
                        arguments = mapOf("query" to JsonPrimitive("spring")),
                    )

                val response = searchDependencyTool.execute(request)

                assertThat(response.isError).isFalse()
                assertThat(response.content.first().text).contains("Found 2 dependencies for query: 'spring'")
                assertThat(response.content.first().text).contains("org.springframework:spring-core")
                assertThat(response.content.first().text).contains("org.springframework.boot:spring-boot-starter")
            }

        @Test
        @DisplayName("Should execute search successfully with custom limit")
        fun shouldExecuteSearchSuccessfullyWithCustomLimit() =
            runTest {
                val dependencies =
                    listOf(
                        Dependency(
                            groupId = "org.springframework",
                            artifactId = "spring-core",
                            description = "Spring Framework Core",
                        ),
                    )

                val searchResult =
                    SearchResult(
                        dependencies = dependencies,
                        totalResults = dependencies.size,
                        query = "spring",
                    )

                coEvery { mockMavenRepositoryClient.searchDependencies("spring") } returns Result.success(searchResult)

                val request =
                    MCPToolRequest(
                        name = "search_dependency",
                        arguments =
                            mapOf(
                                "query" to JsonPrimitive("spring"),
                                "limit" to JsonPrimitive("5"),
                            ),
                    )

                val response = searchDependencyTool.execute(request)

                assertThat(response.isError).isFalse()
                assertThat(response.content.first().text).contains("Found 1 dependencies for query: 'spring'")
            }

        @Test
        @DisplayName("Should handle empty search results")
        fun shouldHandleEmptySearchResults() =
            runTest {
                val searchResult =
                    SearchResult(
                        dependencies = emptyList(),
                        totalResults = 0,
                        query = "nonexistent",
                    )

                coEvery { mockMavenRepositoryClient.searchDependencies("nonexistent") } returns Result.success(searchResult)

                val request =
                    MCPToolRequest(
                        name = "search_dependency",
                        arguments = mapOf("query" to JsonPrimitive("nonexistent")),
                    )

                val response = searchDependencyTool.execute(request)

                assertThat(response.isError).isFalse()
                assertThat(response.content.first().text).contains("No dependencies found for query: 'nonexistent'")
            }
    }

    @Nested
    @DisplayName("Error Handling")
    inner class ErrorHandlingTests {
        @Test
        @DisplayName("Should handle repository client errors")
        fun shouldHandleRepositoryClientErrors() =
            runTest {
                coEvery { mockMavenRepositoryClient.searchDependencies("spring") } returns Result.failure(RuntimeException("Network error"))

                val request =
                    MCPToolRequest(
                        name = "search_dependency",
                        arguments = mapOf("query" to JsonPrimitive("spring")),
                    )

                val response = searchDependencyTool.execute(request)

                assertThat(response.isError).isTrue()
                assertThat(response.content.first().text).contains("Search failed: Network error")
            }
    }
}
