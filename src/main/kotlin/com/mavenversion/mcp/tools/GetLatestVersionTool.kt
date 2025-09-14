package com.mavenversion.mcp.tools

import com.mavenversion.mcp.client.MCPContent
import com.mavenversion.mcp.client.MCPProperty
import com.mavenversion.mcp.client.MCPSchema
import com.mavenversion.mcp.client.MCPTool
import com.mavenversion.mcp.client.MCPToolRequest
import com.mavenversion.mcp.client.MCPToolResponse
import com.mavenversion.mcp.models.Version
import com.mavenversion.mcp.server.MCPToolInterface
import com.mavenversion.mcp.web.MavenRepositoryClient
import kotlinx.serialization.json.jsonPrimitive
import mu.KotlinLogging

private val log = KotlinLogging.logger {}

/**
 * MCP tool for getting the latest version of a specific dependency
 * Implements Requirement 2: Retrieve the latest version of a specific Maven dependency
 */
class GetLatestVersionTool(
    private val mavenRepositoryClient: MavenRepositoryClient,
) : MCPToolInterface {
    /**
     * Get the MCP tool definition
     */
    override fun getToolDefinition(): MCPTool {
        return MCPTool(
            name = "get_latest_version",
            description = "Get the latest version of a specific Maven dependency from mvnrepository.com",
            inputSchema =
                MCPSchema(
                    type = "object",
                    properties =
                        mapOf(
                            "groupId" to
                                MCPProperty(
                                    type = "string",
                                    description = "The group ID of the dependency (e.g., 'org.springframework')",
                                ),
                            "artifactId" to
                                MCPProperty(
                                    type = "string",
                                    description = "The artifact ID of the dependency (e.g., 'spring-core')",
                                ),
                        ),
                    required = listOf("groupId", "artifactId"),
                ),
        )
    }

    /**
     * Execute the get latest version tool
     */
    override suspend fun execute(request: MCPToolRequest): MCPToolResponse {
        return try {
            log.debug { "Executing get latest version tool with request: $request" }

            // Validate and extract parameters
            val groupId = extractStringParameter(request, "groupId")
            val artifactId = extractStringParameter(request, "artifactId")

            // Validate parameters
            validateParameters(groupId, artifactId)

            // Get the latest version
            val latestVersion = getLatestVersion(groupId, artifactId)

            // Format response
            formatSuccessResponse(groupId, artifactId, latestVersion)
        } catch (e: IllegalArgumentException) {
            log.warn { "Invalid parameters for get latest version tool: ${e.message}" }
            formatErrorResponse("Invalid parameters: ${e.message}")
        } catch (e: Exception) {
            log.error(e) { "Error executing get latest version tool" }
            formatErrorResponse("Failed to get latest version: ${e.message}")
        }
    }

    /**
     * Extract string parameter from request
     */
    private fun extractStringParameter(
        request: MCPToolRequest,
        paramName: String,
    ): String {
        val value =
            request.arguments[paramName]?.jsonPrimitive?.content
                ?: throw IllegalArgumentException("Missing required parameter: $paramName")
        return value
    }

    /**
     * Validate input parameters
     */
    private fun validateParameters(
        groupId: String,
        artifactId: String,
    ) {
        if (groupId.isBlank()) {
            throw IllegalArgumentException("Group ID cannot be empty")
        }
        if (artifactId.isBlank()) {
            throw IllegalArgumentException("Artifact ID cannot be empty")
        }
        if (groupId.length < 2) {
            throw IllegalArgumentException("Group ID must be at least 2 characters long")
        }
        if (artifactId.length < 2) {
            throw IllegalArgumentException("Artifact ID must be at least 2 characters long")
        }
    }

    /**
     * Get the latest version for the specified dependency
     */
    private suspend fun getLatestVersion(
        groupId: String,
        artifactId: String,
    ): Version {
        log.info { "Getting latest version for dependency: $groupId:$artifactId" }

        val latestVersion =
            mavenRepositoryClient.getLatestVersion(groupId, artifactId).getOrThrow()
                ?: throw IllegalArgumentException("Dependency not found: $groupId:$artifactId")

        log.info { "Found latest version: ${latestVersion.version} for $groupId:$artifactId" }

        return latestVersion
    }

    /**
     * Format successful response
     */
    private fun formatSuccessResponse(
        groupId: String,
        artifactId: String,
        version: Version,
    ): MCPToolResponse {
        val responseText =
            buildString {
                appendLine("Latest version for $groupId:$artifactId")
                appendLine("Version: ${version.version}")
                version.releaseDate?.let { date ->
                    appendLine("Release Date: $date")
                }
                version.downloads?.let { downloads ->
                    appendLine("Downloads: $downloads")
                }
                version.vulnerabilities?.let { vulns ->
                    if (vulns > 0) {
                        appendLine("⚠️  Vulnerabilities: $vulns")
                    }
                }
            }

        return MCPToolResponse(
            content =
                listOf(
                    MCPContent(
                        type = "text",
                        text = responseText,
                    ),
                ),
            isError = false,
        )
    }

    /**
     * Format error response
     */
    private fun formatErrorResponse(message: String): MCPToolResponse {
        return MCPToolResponse(
            content =
                listOf(
                    MCPContent(
                        type = "text",
                        text = "Error: $message",
                    ),
                ),
            isError = true,
        )
    }
}
