package com.mavenversion.mcp.files

import mu.KotlinLogging
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.isReadable
import kotlin.io.path.isWritable
import kotlin.io.path.readText
import kotlin.io.path.writeText

private val log = KotlinLogging.logger {}

/**
 * Manages Gradle build file operations including dependency management
 * Supports both Groovy (build.gradle) and Kotlin DSL (build.gradle.kts) formats
 */
class GradleFileManager {
    /**
     * Represents a Gradle dependency
     */
    data class GradleDependency(
        // implementation, testImplementation, etc.
        val configuration: String,
        val groupId: String,
        val artifactId: String,
        val version: String? = null,
        val classifier: String? = null,
        val extension: String? = null,
    ) {
        /**
         * Get the dependency coordinate string
         */
        fun getCoordinate(): String {
            val base = "$groupId:$artifactId"
            val versionPart = if (version != null) ":$version" else ""
            val classifierPart = if (classifier != null) ":$classifier" else ""
            val extensionPart = if (extension != null) "@$extension" else ""
            return "$base$versionPart$classifierPart$extensionPart"
        }

        /**
         * Check if this dependency matches another by groupId and artifactId
         */
        fun matches(other: GradleDependency): Boolean {
            return groupId == other.groupId && artifactId == other.artifactId
        }
    }

    /**
     * Result of a dependency operation
     */
    data class DependencyOperationResult(
        val success: Boolean,
        val message: String,
        val updatedDependencies: List<GradleDependency> = emptyList(),
        val backupPath: Path? = null,
    )

    /**
     * Supported Gradle file types
     */
    enum class GradleFileType {
        GROOVY, // build.gradle
        KOTLIN_DSL, // build.gradle.kts
    }

    /**
     * Read and parse a Gradle build file
     */
    fun readGradleFile(gradlePath: Path): Result<GradleFileContent> =
        runCatching {
            log.debug { "Reading Gradle file: $gradlePath" }

            if (!gradlePath.exists()) {
                throw GradleFileException("Gradle file does not exist: $gradlePath")
            }

            if (!gradlePath.isReadable()) {
                throw GradleFileException("Gradle file is not readable: $gradlePath")
            }

            val content = gradlePath.readText()
            val fileType = determineFileType(gradlePath)

            // Validate basic structure
            validateGradleStructure(content, fileType)

            log.debug { "Successfully parsed Gradle file: $gradlePath (${fileType.name})" }
            GradleFileContent(content, fileType, gradlePath)
        }.onFailure { error ->
            log.error(error) { "Failed to read Gradle file: $gradlePath" }
        }

    /**
     * Extract all dependencies from a Gradle file content
     */
    fun extractDependencies(gradleContent: GradleFileContent): List<GradleDependency> {
        log.debug { "Extracting dependencies from Gradle file" }

        val dependencies = mutableListOf<GradleDependency>()

        when (gradleContent.fileType) {
            GradleFileType.GROOVY -> {
                dependencies.addAll(extractDependenciesFromGroovy(gradleContent.content))
            }
            GradleFileType.KOTLIN_DSL -> {
                dependencies.addAll(extractDependenciesFromKotlinDsl(gradleContent.content))
            }
        }

        log.debug { "Extracted ${dependencies.size} dependencies from Gradle file" }
        return dependencies
    }

    /**
     * Update a dependency version in the Gradle file content
     */
    fun updateDependencyVersion(
        gradleContent: GradleFileContent,
        groupId: String,
        artifactId: String,
        newVersion: String,
    ): Result<DependencyOperationResult> =
        runCatching {
            log.debug { "Updating dependency version: $groupId:$artifactId to $newVersion" }

            val updatedContent =
                when (gradleContent.fileType) {
                    GradleFileType.GROOVY -> {
                        updateDependencyVersionInGroovy(gradleContent.content, groupId, artifactId, newVersion)
                    }
                    GradleFileType.KOTLIN_DSL -> {
                        updateDependencyVersionInKotlinDsl(gradleContent.content, groupId, artifactId, newVersion)
                    }
                }

            val updatedDependencies =
                extractDependencies(GradleFileContent(updatedContent, gradleContent.fileType, gradleContent.path))
                    .filter { it.groupId == groupId && it.artifactId == artifactId }

            if (updatedDependencies.isEmpty()) {
                return@runCatching DependencyOperationResult(
                    success = false,
                    message = "Dependency $groupId:$artifactId not found in Gradle file",
                )
            }

            log.info { "Updated dependency $groupId:$artifactId to version $newVersion" }
            DependencyOperationResult(
                success = true,
                message = "Successfully updated dependency $groupId:$artifactId to version $newVersion",
                updatedDependencies = updatedDependencies,
            )
        }.onFailure { error ->
            log.error(error) { "Failed to update dependency version: $groupId:$artifactId" }
        }

    /**
     * Add a new dependency to the Gradle file content
     */
    fun addDependency(
        gradleContent: GradleFileContent,
        dependency: GradleDependency,
    ): Result<DependencyOperationResult> =
        runCatching {
            log.debug { "Adding new dependency: ${dependency.getCoordinate()}" }

            // Check if dependency already exists
            val existingDependencies = extractDependencies(gradleContent)
            if (existingDependencies.any { it.matches(dependency) }) {
                return@runCatching DependencyOperationResult(
                    success = false,
                    message = "Dependency ${dependency.groupId}:${dependency.artifactId} already exists",
                )
            }

            val updatedContent =
                when (gradleContent.fileType) {
                    GradleFileType.GROOVY -> {
                        addDependencyToGroovy(gradleContent.content, dependency)
                    }
                    GradleFileType.KOTLIN_DSL -> {
                        addDependencyToKotlinDsl(gradleContent.content, dependency)
                    }
                }

            log.info { "Successfully added dependency: ${dependency.getCoordinate()}" }
            DependencyOperationResult(
                success = true,
                message = "Successfully added dependency ${dependency.getCoordinate()}",
                updatedDependencies = listOf(dependency),
            )
        }.onFailure { error ->
            log.error(error) { "Failed to add dependency: ${dependency.getCoordinate()}" }
        }

    /**
     * Write a Gradle file content to a file
     */
    fun writeGradleFile(
        gradleContent: GradleFileContent,
        gradlePath: Path,
        createBackup: Boolean = true,
    ): Result<DependencyOperationResult> =
        runCatching {
            log.debug { "Writing Gradle file: $gradlePath" }

            if (!gradlePath.parent.exists()) {
                throw GradleFileException("Parent directory does not exist: ${gradlePath.parent}")
            }

            if (gradlePath.exists() && !gradlePath.isWritable()) {
                throw GradleFileException("Gradle file is not writable: $gradlePath")
            }

            var backupPath: Path? = null

            // Create backup if requested and file exists
            if (createBackup && gradlePath.exists()) {
                backupPath = createBackup(gradlePath)
                log.debug { "Created backup: $backupPath" }
            }

            // Validate content before writing
            validateGradleStructure(gradleContent.content, gradleContent.fileType)

            // Write the content
            gradlePath.writeText(gradleContent.content)

            log.info { "Successfully wrote Gradle file: $gradlePath" }
            DependencyOperationResult(
                success = true,
                message = "Successfully wrote Gradle file",
                backupPath = backupPath,
            )
        }.onFailure { error ->
            log.error(error) { "Failed to write Gradle file: $gradlePath" }
        }

    /**
     * Determine the type of Gradle file based on its path
     */
    private fun determineFileType(gradlePath: Path): GradleFileType {
        return when (gradlePath.fileName.toString()) {
            "build.gradle.kts" -> GradleFileType.KOTLIN_DSL
            "build.gradle" -> GradleFileType.GROOVY
            else -> {
                // Try to determine by content
                val content = gradlePath.readText()
                if (content.contains("implementation(") || content.contains("testImplementation(")) {
                    GradleFileType.KOTLIN_DSL
                } else {
                    GradleFileType.GROOVY
                }
            }
        }
    }

    /**
     * Validate basic Gradle file structure
     */
    private fun validateGradleStructure(
        content: String,
        fileType: GradleFileType,
    ) {
        when (fileType) {
            GradleFileType.GROOVY -> {
                // Basic Groovy validation
                if (!content.contains("dependencies") && !content.contains("plugins")) {
                    throw GradleFileException("Invalid Groovy Gradle file: missing dependencies or plugins block")
                }
            }
            GradleFileType.KOTLIN_DSL -> {
                // Basic Kotlin DSL validation
                if (!content.contains("dependencies") && !content.contains("plugins")) {
                    throw GradleFileException("Invalid Kotlin DSL Gradle file: missing dependencies or plugins block")
                }
            }
        }
        log.debug { "Gradle file structure validation passed" }
    }

    /**
     * Extract dependencies from Groovy build.gradle content
     */
    private fun extractDependenciesFromGroovy(content: String): List<GradleDependency> {
        val dependencies = mutableListOf<GradleDependency>()

        // Find dependencies block
        val dependenciesRegex = Regex("""dependencies\s*\{([^}]*(?:\{[^}]*\}[^}]*)*)\}""", RegexOption.DOT_MATCHES_ALL)
        val dependenciesMatch = dependenciesRegex.find(content)

        if (dependenciesMatch != null) {
            val dependenciesBlock = dependenciesMatch.groupValues[1]

            // Parse different dependency configurations
            val dependencyRegex = Regex("""(\w+)\s+['"]([^:]+):([^:]+)(?::([^'"]+))?['"]""")
            dependencyRegex.findAll(dependenciesBlock).forEach { match ->
                val configuration = match.groupValues[1]
                val groupId = match.groupValues[2]
                val artifactId = match.groupValues[3]
                val version = match.groupValues[4].takeIf { it.isNotEmpty() }

                dependencies.add(
                    GradleDependency(
                        configuration = configuration,
                        groupId = groupId,
                        artifactId = artifactId,
                        version = version,
                    ),
                )
            }
        }

        return dependencies
    }

    /**
     * Extract dependencies from Kotlin DSL build.gradle.kts content
     */
    private fun extractDependenciesFromKotlinDsl(content: String): List<GradleDependency> {
        val dependencies = mutableListOf<GradleDependency>()

        // Find dependencies block
        val dependenciesRegex = Regex("""dependencies\s*\{([^}]*(?:\{[^}]*\}[^}]*)*)\}""", RegexOption.DOT_MATCHES_ALL)
        val dependenciesMatch = dependenciesRegex.find(content)

        if (dependenciesMatch != null) {
            val dependenciesBlock = dependenciesMatch.groupValues[1]

            // Parse different dependency configurations
            val dependencyRegex = Regex("""(\w+)\s*\(\s*['"]([^:]+):([^:]+)(?::([^'"]+))?['"]\s*\)""")
            dependencyRegex.findAll(dependenciesBlock).forEach { match ->
                val configuration = match.groupValues[1]
                val groupId = match.groupValues[2]
                val artifactId = match.groupValues[3]
                val version = match.groupValues[4].takeIf { it.isNotEmpty() }

                dependencies.add(
                    GradleDependency(
                        configuration = configuration,
                        groupId = groupId,
                        artifactId = artifactId,
                        version = version,
                    ),
                )
            }
        }

        return dependencies
    }

    /**
     * Update dependency version in Groovy content
     */
    private fun updateDependencyVersionInGroovy(
        content: String,
        groupId: String,
        artifactId: String,
        newVersion: String,
    ): String {
        val dependencyPattern = Regex("""(\w+)\s+['"]$groupId:$artifactId(?::[^'"]*)?['"]""")

        return dependencyPattern.replace(content) { match ->
            val configuration = match.groupValues[1]
            "$configuration '$groupId:$artifactId:$newVersion'"
        }
    }

    /**
     * Update dependency version in Kotlin DSL content
     */
    private fun updateDependencyVersionInKotlinDsl(
        content: String,
        groupId: String,
        artifactId: String,
        newVersion: String,
    ): String {
        val dependencyPattern = Regex("""(\w+)\s*\(\s*['"]$groupId:$artifactId(?::[^'"]*)?['"]\s*\)""")

        return dependencyPattern.replace(content) { match ->
            val configuration = match.groupValues[1]
            "$configuration(\"$groupId:$artifactId:$newVersion\")"
        }
    }

    /**
     * Add dependency to Groovy content
     */
    private fun addDependencyToGroovy(
        content: String,
        dependency: GradleDependency,
    ): String {
        val dependenciesRegex = Regex("""(dependencies\s*\{)""")
        val match = dependenciesRegex.find(content)

        return if (match != null) {
            val insertionPoint = match.range.last + 1
            val newDependency = "\n    ${dependency.configuration} '${dependency.getCoordinate()}'"
            content.substring(0, insertionPoint) + newDependency + content.substring(insertionPoint)
        } else {
            // Add dependencies block if it doesn't exist
            content + "\n\ndependencies {\n    ${dependency.configuration} '${dependency.getCoordinate()}'\n}"
        }
    }

    /**
     * Add dependency to Kotlin DSL content
     */
    private fun addDependencyToKotlinDsl(
        content: String,
        dependency: GradleDependency,
    ): String {
        val dependenciesRegex = Regex("""(dependencies\s*\{)""")
        val match = dependenciesRegex.find(content)

        return if (match != null) {
            val insertionPoint = match.range.last + 1
            val newDependency = "\n    ${dependency.configuration}(\"${dependency.getCoordinate()}\")"
            content.substring(0, insertionPoint) + newDependency + content.substring(insertionPoint)
        } else {
            // Add dependencies block if it doesn't exist
            content + "\n\ndependencies {\n    ${dependency.configuration}(\"${dependency.getCoordinate()}\")\n}"
        }
    }

    /**
     * Create a backup of the Gradle file
     */
    private fun createBackup(gradlePath: Path): Path {
        val timestamp = System.currentTimeMillis()
        val backupPath = gradlePath.parent.resolve("${gradlePath.fileName}.backup.$timestamp")

        gradlePath.toFile().copyTo(backupPath.toFile(), overwrite = true)
        return backupPath
    }
}

/**
 * Represents the content of a Gradle build file
 */
data class GradleFileContent(
    val content: String,
    val fileType: GradleFileManager.GradleFileType,
    val path: Path,
)

/**
 * Exception thrown by GradleFileManager operations
 */
class GradleFileException(message: String, cause: Throwable? = null) : Exception(message, cause)
