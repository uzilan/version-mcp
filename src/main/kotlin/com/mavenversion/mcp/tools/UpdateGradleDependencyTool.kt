package com.mavenversion.mcp.tools

import com.mavenversion.mcp.client.MCPContent
import com.mavenversion.mcp.client.MCPProperty
import com.mavenversion.mcp.client.MCPSchema
import com.mavenversion.mcp.client.MCPTool
import com.mavenversion.mcp.client.MCPToolRequest
import com.mavenversion.mcp.client.MCPToolResponse
import com.mavenversion.mcp.files.GradleFileManager
import com.mavenversion.mcp.files.ProjectFileDetector
import com.mavenversion.mcp.models.UpdateResult
import com.mavenversion.mcp.server.MCPToolInterface
import com.mavenversion.mcp.web.MavenRepositoryClient
import kotlinx.serialization.json.jsonPrimitive
import mu.KotlinLogging
import java.nio.file.Path

private val log = KotlinLogging.logger {}

/**
 * MCP tool for updating Gradle dependencies in build.gradle files
 * Implements Requirement 5: Automatically update dependency versions in Gradle projects
 */
class UpdateGradleDependencyTool(
    private val mavenRepositoryClient: MavenRepositoryClient,
    private val projectFileDetector: ProjectFileDetector,
    private val gradleFileManager: GradleFileManager,
    private val errorHandlingService: com.mavenversion.mcp.service.ErrorHandlingService,
    private val errorRecoveryService: com.mavenversion.mcp.recovery.ErrorRecoveryService,
) : MCPToolInterface {
    /**
     * Get the MCP tool definition
     */
    override fun getToolDefinition(): MCPTool {
        return MCPTool(
            name = "update_gradle_dependency",
            description = "Update or add a Gradle dependency in a build.gradle or build.gradle.kts file",
            inputSchema =
                MCPSchema(
                    type = "object",
                    properties =
                        mapOf(
                            "projectPath" to
                                MCPProperty(
                                    type = "string",
                                    description = "Path to the Gradle project directory containing build.gradle or build.gradle.kts",
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
                            "configuration" to
                                MCPProperty(
                                    type = "string",
                                    description =
                                        "The dependency configuration (optional, e.g., 'implementation', " +
                                            "'testImplementation', 'compileOnly')",
                                ),
                            "createBackup" to
                                MCPProperty(
                                    type = "boolean",
                                    description = "Whether to create a backup of the build file (default: true)",
                                ),
                        ),
                    required = listOf("projectPath", "groupId", "artifactId"),
                ),
        )
    }

    /**
     * Execute the update Gradle dependency tool
     */
    override suspend fun execute(request: MCPToolRequest): MCPToolResponse {
        return try {
            log.debug { "Executing update Gradle dependency tool with request: $request" }

            // Validate and extract parameters
            val projectPath = extractRequiredStringParameter(request, "projectPath")
            val groupId = extractRequiredStringParameter(request, "groupId")
            val artifactId = extractRequiredStringParameter(request, "artifactId")
            val version = extractStringParameter(request, "version")
            val configuration = extractStringParameter(request, "configuration")
            val createBackup = extractBooleanParameter(request, "createBackup") ?: true

            // Validate parameters
            validateParameters(projectPath, groupId, artifactId, version, configuration)

            // Update the dependency
            val updateResult = updateGradleDependency(projectPath, groupId, artifactId, version, configuration, createBackup)

            // Format response
            formatSuccessResponse(updateResult)
        } catch (e: IllegalArgumentException) {
            log.warn { "Invalid parameters for update Gradle dependency tool: ${e.message}" }
            formatErrorResponse("Invalid parameters: ${e.message}")
        } catch (e: Exception) {
            log.error(e) { "Error executing update Gradle dependency tool" }
            formatErrorResponse("Failed to update Gradle dependency: ${e.message}")
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
        configuration: String?,
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
        if (configuration != null && configuration.isNotBlank()) {
            val validConfigurations =
                listOf(
                    "implementation", "api", "compileOnly", "runtimeOnly",
                    "testImplementation", "testCompileOnly", "testRuntimeOnly",
                    "annotationProcessor", "kapt", "ksp",
                )
            if (configuration !in validConfigurations) {
                throw IllegalArgumentException(
                    "Invalid configuration: $configuration. Valid configurations are: ${validConfigurations.joinToString(", ")}",
                )
            }
        }
    }

    /**
     * Update the Gradle dependency
     */
    private suspend fun updateGradleDependency(
        projectPath: String,
        groupId: String,
        artifactId: String,
        version: String?,
        configuration: String?,
        createBackup: Boolean,
    ): UpdateResult {
        log.info { "Updating Gradle dependency: $groupId:$artifactId in project: $projectPath" }

        val projectDir = Path.of(projectPath)
        if (!projectDir.toFile().exists()) {
            throw IllegalArgumentException("Project directory does not exist: $projectPath")
        }

        // Detect project type and find build.gradle file
        val projectInfo = projectFileDetector.detectProject(projectPath).getOrThrow()
        if (projectInfo.type != ProjectFileDetector.ProjectType.GRADLE && projectInfo.type != ProjectFileDetector.ProjectType.MIXED) {
            throw IllegalArgumentException("Project is not a Gradle project: $projectPath")
        }

        val buildFile =
            projectInfo.buildFiles.find {
                it.type == ProjectFileDetector.BuildFileType.GRADLE_GROOVY ||
                    it.type == ProjectFileDetector.BuildFileType.GRADLE_KOTLIN
            } ?: throw IllegalArgumentException("No build.gradle or build.gradle.kts file found in project: $projectPath")

        // Determine the version to use
        val targetVersion = version ?: getLatestVersion(groupId, artifactId)

        // Read and parse the Gradle file
        val gradleContent = gradleFileManager.readGradleFile(buildFile.path).getOrThrow()

        // Check if dependency already exists
        val existingDependencies = gradleFileManager.extractDependencies(gradleContent)
        val existingDependency = existingDependencies.find { it.groupId == groupId && it.artifactId == artifactId }

        val result =
            if (existingDependency != null) {
                // Update existing dependency
                log.info { "Updating existing dependency $groupId:$artifactId from ${existingDependency.version} to $targetVersion" }
                val updateResult = gradleFileManager.updateDependencyVersion(gradleContent, groupId, artifactId, targetVersion).getOrThrow()
                gradleFileManager.writeGradleFile(gradleContent, buildFile.path, createBackup).getOrThrow()

                UpdateResult(
                    success = true,
                    message = "Successfully updated dependency $groupId:$artifactId to version $targetVersion",
                    filePath = buildFile.path.toString(),
                    oldVersion = existingDependency.version,
                    newVersion = targetVersion,
                    wasAdded = false,
                )
            } else {
                // Add new dependency
                log.info { "Adding new dependency $groupId:$artifactId with version $targetVersion" }
                val newDependency =
                    GradleFileManager.GradleDependency(
                        configuration = configuration ?: "implementation",
                        groupId = groupId,
                        artifactId = artifactId,
                        version = targetVersion,
                    )
                val addResult = gradleFileManager.addDependency(gradleContent, newDependency).getOrThrow()
                gradleFileManager.writeGradleFile(gradleContent, buildFile.path, createBackup).getOrThrow()

                UpdateResult(
                    success = true,
                    message = "Successfully added dependency $groupId:$artifactId with version $targetVersion",
                    filePath = buildFile.path.toString(),
                    oldVersion = null,
                    newVersion = targetVersion,
                    wasAdded = true,
                )
            }

        log.info { "Gradle dependency update completed: ${result.message}" }
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
