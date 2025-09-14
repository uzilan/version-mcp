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
 * MCP tool for getting all versions of a specific dependency
 * Implements Requirement 3: Retrieve all available versions of a Maven dependency
 */
class GetAllVersionsTool(
    private val mavenRepositoryClient: MavenRepositoryClient,
) : MCPToolInterface {
    /**
     * Get the MCP tool definition
     */
    override fun getToolDefinition(): MCPTool {
        return MCPTool(
            name = "get_all_versions",
            description = "Get all available versions of a specific Maven dependency from mvnrepository.com",
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
                            "limit" to
                                MCPProperty(
                                    type = "integer",
                                    description = "Maximum number of versions to return (default: 50, max: 200)",
                                ),
                            "includeSnapshots" to
                                MCPProperty(
                                    type = "boolean",
                                    description = "Whether to include snapshot versions (default: false)",
                                ),
                            "minVersion" to
                                MCPProperty(
                                    type = "string",
                                    description = "Minimum version to include (optional, e.g., '1.0.0')",
                                ),
                        ),
                    required = listOf("groupId", "artifactId"),
                ),
        )
    }

    /**
     * Execute the get all versions tool
     */
    override suspend fun execute(request: MCPToolRequest): MCPToolResponse {
        return try {
            log.debug { "Executing get all versions tool with request: $request" }

            // Validate and extract parameters
            val groupId = extractRequiredStringParameter(request, "groupId")
            val artifactId = extractRequiredStringParameter(request, "artifactId")
            val limit = extractIntParameter(request, "limit") ?: 50
            val includeSnapshots = extractBooleanParameter(request, "includeSnapshots") ?: false
            val minVersion = extractStringParameter(request, "minVersion")

            // Validate parameters
            validateParameters(groupId, artifactId, limit)

            // Get all versions
            val versions = getAllVersions(groupId, artifactId, limit, includeSnapshots, minVersion)

            // Format response
            formatSuccessResponse(groupId, artifactId, versions)
        } catch (e: IllegalArgumentException) {
            log.warn { "Invalid parameters for get all versions tool: ${e.message}" }
            formatErrorResponse("Invalid parameters: ${e.message}")
        } catch (e: Exception) {
            log.error(e) { "Error executing get all versions tool" }
            formatErrorResponse("Failed to get versions: ${e.message}")
        }
    }

    /**
     * Extract string parameter from request
     */
    private fun extractStringParameter(
        request: MCPToolRequest,
        paramName: String,
    ): String? {
        return request.arguments[paramName]?.jsonPrimitive?.content
    }

    /**
     * Extract required string parameter from request
     */
    private fun extractRequiredStringParameter(
        request: MCPToolRequest,
        paramName: String,
    ): String {
        return extractStringParameter(request, paramName)
            ?: throw IllegalArgumentException("Missing required parameter: $paramName")
    }

    /**
     * Extract optional integer parameter from request
     */
    private fun extractIntParameter(
        request: MCPToolRequest,
        paramName: String,
    ): Int? {
        return request.arguments[paramName]?.jsonPrimitive?.content?.toIntOrNull()
    }

    /**
     * Extract optional boolean parameter from request
     */
    private fun extractBooleanParameter(
        request: MCPToolRequest,
        paramName: String,
    ): Boolean? {
        return request.arguments[paramName]?.jsonPrimitive?.content?.toBooleanStrictOrNull()
    }

    /**
     * Validate input parameters
     */
    private fun validateParameters(
        groupId: String,
        artifactId: String,
        limit: Int,
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
        if (limit < 1 || limit > 200) {
            throw IllegalArgumentException("Limit must be between 1 and 200")
        }
    }

    /**
     * Get all versions for the specified dependency
     */
    private suspend fun getAllVersions(
        groupId: String,
        artifactId: String,
        limit: Int,
        includeSnapshots: Boolean,
        minVersion: String?,
    ): List<Version> {
        log.info { "Getting all versions for dependency: $groupId:$artifactId (limit: $limit, snapshots: $includeSnapshots)" }

        val allVersions = mavenRepositoryClient.getAllVersions(groupId, artifactId).getOrThrow()

        // Apply filters
        val filteredVersions =
            allVersions.filter { version ->
                // Filter out snapshots if not requested
                if (!includeSnapshots && version.version.contains("SNAPSHOT", ignoreCase = true)) {
                    return@filter false
                }

                // Apply minimum version filter if specified
                if (minVersion != null) {
                    try {
                        val comparison = Version.compareVersions(version.version, minVersion)
                        if (comparison < 0) {
                            return@filter false
                        }
                    } catch (e: Exception) {
                        log.warn { "Could not compare version ${version.version} with minimum $minVersion: ${e.message}" }
                    }
                }

                true
            }

        // Sort by version (newest first) and limit results
        val sortedVersions = filteredVersions.sortedDescending().take(limit)

        log.info { "Found ${sortedVersions.size} versions for $groupId:$artifactId" }

        return sortedVersions
    }

    /**
     * Format successful response
     */
    private fun formatSuccessResponse(
        groupId: String,
        artifactId: String,
        versions: List<Version>,
    ): MCPToolResponse {
        val responseText =
            if (versions.isEmpty()) {
                "No versions found for dependency: $groupId:$artifactId"
            } else {
                buildString {
                    appendLine("All versions for $groupId:$artifactId (${versions.size} versions)")
                    appendLine()
                    versions.forEachIndexed { index, version ->
                        val versionText =
                            if (version.isLatest) {
                                "🌟 ${version.version} (LATEST)"
                            } else {
                                version.version
                            }
                        appendLine("${index + 1}. $versionText")
                        version.releaseDate?.let { date ->
                            appendLine("   Release Date: $date")
                        }
                        version.downloads?.let { downloads ->
                            appendLine("   Downloads: $downloads")
                        }
                        version.vulnerabilities?.let { vulns ->
                            if (vulns > 0) {
                                appendLine("   ⚠️  Vulnerabilities: $vulns")
                            }
                        }
                        appendLine()
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
