package com.mavenversion.mcp.tools

import com.mavenversion.mcp.client.MCPContent
import com.mavenversion.mcp.client.MCPProperty
import com.mavenversion.mcp.client.MCPSchema
import com.mavenversion.mcp.client.MCPTool
import com.mavenversion.mcp.client.MCPToolRequest
import com.mavenversion.mcp.client.MCPToolResponse
import com.mavenversion.mcp.files.MavenFileManager
import com.mavenversion.mcp.files.ProjectFileDetector
import com.mavenversion.mcp.models.UpdateResult
import com.mavenversion.mcp.web.MavenRepositoryClient
import kotlinx.serialization.json.jsonPrimitive
import mu.KotlinLogging
import java.nio.file.Path

private val log = KotlinLogging.logger {}

/**
 * MCP tool for updating Maven dependencies in pom.xml files
 * Implements Requirement 4: Automatically update dependency versions in Maven projects
 */
class UpdateMavenDependencyTool(
    private val mavenRepositoryClient: MavenRepositoryClient,
    private val projectFileDetector: ProjectFileDetector,
    private val mavenFileManager: MavenFileManager,
) {
    /**
     * Get the MCP tool definition
     */
    fun getToolDefinition(): MCPTool {
        return MCPTool(
            name = "update_maven_dependency",
            description = "Update or add a Maven dependency in a pom.xml file",
            inputSchema =
                MCPSchema(
                    type = "object",
                    properties =
                        mapOf(
                            "projectPath" to
                                MCPProperty(
                                    type = "string",
                                    description = "Path to the Maven project directory containing pom.xml",
                                ),
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
                            "version" to
                                MCPProperty(
                                    type = "string",
                                    description = "The version to set (optional, if not provided, latest version will be used)",
                                ),
                            "scope" to
                                MCPProperty(
                                    type = "string",
                                    description = "The dependency scope (optional, e.g., 'compile', 'test', 'provided')",
                                ),
                            "createBackup" to
                                MCPProperty(
                                    type = "boolean",
                                    description = "Whether to create a backup of the pom.xml file (default: true)",
                                ),
                        ),
                    required = listOf("projectPath", "groupId", "artifactId"),
                ),
        )
    }

    /**
     * Execute the update Maven dependency tool
     */
    suspend fun execute(request: MCPToolRequest): MCPToolResponse {
        return try {
            log.debug { "Executing update Maven dependency tool with request: $request" }

            // Validate and extract parameters
            val projectPath = extractRequiredStringParameter(request, "projectPath")
            val groupId = extractRequiredStringParameter(request, "groupId")
            val artifactId = extractRequiredStringParameter(request, "artifactId")
            val version = extractStringParameter(request, "version")
            val scope = extractStringParameter(request, "scope")
            val createBackup = extractBooleanParameter(request, "createBackup") ?: true

            // Validate parameters
            validateParameters(projectPath, groupId, artifactId, version, scope)

            // Update the dependency
            val updateResult = updateMavenDependency(projectPath, groupId, artifactId, version, scope, createBackup)

            // Format response
            formatSuccessResponse(updateResult)
        } catch (e: IllegalArgumentException) {
            log.warn { "Invalid parameters for update Maven dependency tool: ${e.message}" }
            formatErrorResponse("Invalid parameters: ${e.message}")
        } catch (e: Exception) {
            log.error(e) { "Error executing update Maven dependency tool" }
            formatErrorResponse("Failed to update Maven dependency: ${e.message}")
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
        projectPath: String,
        groupId: String,
        artifactId: String,
        version: String?,
        scope: String?,
    ) {
        if (projectPath.isBlank()) {
            throw IllegalArgumentException("Project path cannot be empty")
        }
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
        if (scope != null && scope.isNotBlank()) {
            val validScopes = listOf("compile", "test", "provided", "runtime", "system", "import")
            if (scope !in validScopes) {
                throw IllegalArgumentException("Invalid scope: $scope. Valid scopes are: ${validScopes.joinToString(", ")}")
            }
        }
    }

    /**
     * Update the Maven dependency
     */
    private suspend fun updateMavenDependency(
        projectPath: String,
        groupId: String,
        artifactId: String,
        version: String?,
        scope: String?,
        createBackup: Boolean,
    ): UpdateResult {
        log.info { "Updating Maven dependency: $groupId:$artifactId in project: $projectPath" }

        val projectDir = Path.of(projectPath)
        if (!projectDir.toFile().exists()) {
            throw IllegalArgumentException("Project directory does not exist: $projectPath")
        }

        // Detect project type and find pom.xml
        val projectInfo = projectFileDetector.detectProject(projectPath).getOrThrow()
        if (projectInfo.type != ProjectFileDetector.ProjectType.MAVEN && projectInfo.type != ProjectFileDetector.ProjectType.MIXED) {
            throw IllegalArgumentException("Project is not a Maven project: $projectPath")
        }

        val pomFile =
            projectInfo.buildFiles.find { it.type == ProjectFileDetector.BuildFileType.MAVEN_POM }
                ?: throw IllegalArgumentException("No pom.xml file found in project: $projectPath")

        // Determine the version to use
        val targetVersion = version ?: getLatestVersion(groupId, artifactId)

        // Read and parse the POM file
        val document = mavenFileManager.readPomFile(pomFile.path).getOrThrow()

        // Check if dependency already exists
        val existingDependencies = mavenFileManager.extractDependencies(document)
        val existingDependency = existingDependencies.find { it.groupId == groupId && it.artifactId == artifactId }

        val result =
            if (existingDependency != null) {
                // Update existing dependency
                log.info { "Updating existing dependency $groupId:$artifactId from ${existingDependency.version} to $targetVersion" }
                val updateResult = mavenFileManager.updateDependencyVersion(document, groupId, artifactId, targetVersion).getOrThrow()
                mavenFileManager.writePomFile(document, pomFile.path, createBackup).getOrThrow()

                UpdateResult(
                    success = true,
                    message = "Successfully updated dependency $groupId:$artifactId to version $targetVersion",
                    filePath = pomFile.path.toString(),
                    oldVersion = existingDependency.version,
                    newVersion = targetVersion,
                    wasAdded = false,
                )
            } else {
                // Add new dependency
                log.info { "Adding new dependency $groupId:$artifactId with version $targetVersion" }
                val newDependency =
                    MavenFileManager.MavenDependency(
                        groupId = groupId,
                        artifactId = artifactId,
                        version = targetVersion,
                        scope = scope,
                    )
                val addResult = mavenFileManager.addDependency(document, newDependency).getOrThrow()
                mavenFileManager.writePomFile(document, pomFile.path, createBackup).getOrThrow()

                UpdateResult(
                    success = true,
                    message = "Successfully added dependency $groupId:$artifactId with version $targetVersion",
                    filePath = pomFile.path.toString(),
                    oldVersion = null,
                    newVersion = targetVersion,
                    wasAdded = true,
                )
            }

        log.info { "Maven dependency update completed: ${result.message}" }
        return result
    }

    /**
     * Get the latest version for a dependency
     */
    private suspend fun getLatestVersion(
        groupId: String,
        artifactId: String,
    ): String {
        log.debug { "Getting latest version for dependency: $groupId:$artifactId" }
        val latestVersion =
            mavenRepositoryClient.getLatestVersion(groupId, artifactId).getOrThrow()
                ?: throw IllegalArgumentException("Dependency not found: $groupId:$artifactId")
        return latestVersion.version
    }

    /**
     * Format successful response
     */
    private fun formatSuccessResponse(updateResult: UpdateResult): MCPToolResponse {
        val responseText =
            buildString {
                appendLine("✅ ${updateResult.message}")
                appendLine("File: ${updateResult.filePath}")
                if (updateResult.wasAdded) {
                    appendLine("Action: Added new dependency")
                } else {
                    appendLine("Action: Updated existing dependency")
                    updateResult.oldVersion?.let { oldVersion ->
                        appendLine("Previous version: $oldVersion")
                    }
                }
                updateResult.newVersion?.let { newVersion ->
                    appendLine("New version: $newVersion")
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
