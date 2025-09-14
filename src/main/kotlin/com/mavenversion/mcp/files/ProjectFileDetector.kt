package com.mavenversion.mcp.files

import mu.KotlinLogging
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.exists
import kotlin.io.path.isReadable
import kotlin.io.path.isWritable

private val log = KotlinLogging.logger {}

/**
 * Detects project type and locates build files for Maven and Gradle projects
 */
class ProjectFileDetector {
    /**
     * Represents the type of project detected
     */
    enum class ProjectType {
        MAVEN,
        GRADLE,
        MIXED,
        UNKNOWN,
    }

    /**
     * Represents a detected project with its build files
     */
    data class ProjectInfo(
        val type: ProjectType,
        val rootPath: Path,
        val buildFiles: List<BuildFile>,
    )

    /**
     * Represents a build file with its metadata
     */
    data class BuildFile(
        val path: Path,
        val type: BuildFileType,
        val isReadable: Boolean,
        val isWritable: Boolean,
        val exists: Boolean,
    )

    /**
     * Types of build files supported
     */
    enum class BuildFileType {
        MAVEN_POM("pom.xml"),
        GRADLE_GROOVY("build.gradle"),
        GRADLE_KOTLIN("build.gradle.kts"),
        ;

        constructor(fileName: String) {
            this.fileName = fileName
        }

        val fileName: String
    }

    /**
     * Detect project type and build files in the given directory
     */
    fun detectProject(projectPath: String): Result<ProjectInfo> =
        runCatching {
            val path = Paths.get(projectPath).toAbsolutePath().normalize()

            log.debug { "Detecting project type in: $path" }

            if (!path.exists()) {
                throw ProjectDetectionException("Project path does not exist: $path")
            }

            if (!Files.isDirectory(path)) {
                throw ProjectDetectionException("Project path is not a directory: $path")
            }

            val buildFiles = detectBuildFiles(path)
            val projectType = determineProjectType(buildFiles)

            log.info { "Detected project type: $projectType with ${buildFiles.size} build files" }

            ProjectInfo(
                type = projectType,
                rootPath = path,
                buildFiles = buildFiles,
            )
        }.onFailure { error ->
            log.error(error) { "Failed to detect project in: $projectPath" }
        }

    /**
     * Detect all build files in the project directory and subdirectories
     */
    private fun detectBuildFiles(projectPath: Path): List<BuildFile> {
        val buildFiles = mutableListOf<BuildFile>()

        // Check for build files in the root directory
        BuildFileType.values().forEach { fileType ->
            val filePath = projectPath.resolve(fileType.fileName)
            if (filePath.exists()) {
                buildFiles.add(createBuildFile(filePath, fileType))
                log.debug { "Found ${fileType.fileName} at: $filePath" }
            }
        }

        // Check for build files in immediate subdirectories (common in multi-module projects)
        try {
            Files.list(projectPath).use { stream ->
                stream
                    .filter { Files.isDirectory(it) }
                    .filter { !it.fileName.toString().startsWith(".") } // Skip hidden directories
                    .forEach { subDir ->
                        BuildFileType.values().forEach { fileType ->
                            val filePath = subDir.resolve(fileType.fileName)
                            if (filePath.exists()) {
                                buildFiles.add(createBuildFile(filePath, fileType))
                                log.debug { "Found ${fileType.fileName} in subdirectory: $filePath" }
                            }
                        }
                    }
            }
        } catch (e: Exception) {
            log.warn(e) { "Error scanning subdirectories in: $projectPath" }
        }

        return buildFiles
    }

    /**
     * Create a BuildFile with accessibility information
     */
    private fun createBuildFile(
        filePath: Path,
        fileType: BuildFileType,
    ): BuildFile {
        return BuildFile(
            path = filePath,
            type = fileType,
            isReadable = filePath.isReadable(),
            isWritable = filePath.isWritable(),
            exists = filePath.exists(),
        )
    }

    /**
     * Determine the project type based on detected build files
     */
    private fun determineProjectType(buildFiles: List<BuildFile>): ProjectType {
        val hasMaven = buildFiles.any { it.type == BuildFileType.MAVEN_POM }
        val hasGradle =
            buildFiles.any {
                it.type == BuildFileType.GRADLE_GROOVY || it.type == BuildFileType.GRADLE_KOTLIN
            }

        return when {
            hasMaven && hasGradle -> {
                log.info { "Detected mixed Maven/Gradle project" }
                ProjectType.MIXED
            }
            hasMaven -> {
                log.info { "Detected Maven project" }
                ProjectType.MAVEN
            }
            hasGradle -> {
                log.info { "Detected Gradle project" }
                ProjectType.GRADLE
            }
            else -> {
                log.warn { "No recognized build files found" }
                ProjectType.UNKNOWN
            }
        }
    }

    /**
     * Find the primary build file for a given project type
     */
    fun findPrimaryBuildFile(
        projectInfo: ProjectInfo,
        preferredType: ProjectType? = null,
    ): BuildFile? {
        val targetType = preferredType ?: projectInfo.type

        return when (targetType) {
            ProjectType.MAVEN -> {
                // Prefer root pom.xml over subdirectory ones
                projectInfo.buildFiles
                    .filter { it.type == BuildFileType.MAVEN_POM }
                    .minByOrNull { it.path.nameCount } // Shortest path (closest to root)
            }
            ProjectType.GRADLE -> {
                // Prefer Kotlin DSL over Groovy, and root over subdirectory
                projectInfo.buildFiles
                    .filter { it.type == BuildFileType.GRADLE_KOTLIN || it.type == BuildFileType.GRADLE_GROOVY }
                    .sortedWith(
                        compareBy<BuildFile> { it.path.nameCount }
                            .thenBy { if (it.type == BuildFileType.GRADLE_KOTLIN) 0 else 1 },
                    )
                    .firstOrNull()
            }
            ProjectType.MIXED -> {
                // For mixed projects, prefer Maven by default
                findPrimaryBuildFile(projectInfo, ProjectType.MAVEN)
                    ?: findPrimaryBuildFile(projectInfo, ProjectType.GRADLE)
            }
            ProjectType.UNKNOWN -> null
        }
    }

    /**
     * Validate that a build file is accessible for read/write operations
     */
    fun validateBuildFileAccess(buildFile: BuildFile): Result<Unit> =
        runCatching {
            when {
                !buildFile.exists -> throw ProjectDetectionException(
                    "Build file does not exist: ${buildFile.path}",
                )
                !buildFile.isReadable -> throw ProjectDetectionException(
                    "Build file is not readable: ${buildFile.path}",
                )
                !buildFile.isWritable -> throw ProjectDetectionException(
                    "Build file is not writable: ${buildFile.path}",
                )
                else -> {
                    log.debug { "Build file access validated: ${buildFile.path}" }
                }
            }
        }

    /**
     * Get all build files of a specific type
     */
    fun getBuildFilesByType(
        projectInfo: ProjectInfo,
        fileType: BuildFileType,
    ): List<BuildFile> {
        return projectInfo.buildFiles.filter { it.type == fileType }
    }

    /**
     * Check if the project has multiple build files of the same type
     */
    fun hasMultipleBuildFiles(
        projectInfo: ProjectInfo,
        fileType: BuildFileType,
    ): Boolean {
        return getBuildFilesByType(projectInfo, fileType).size > 1
    }
}

/**
 * Exception thrown when project detection fails
 */
class ProjectDetectionException(message: String, cause: Throwable? = null) : Exception(message, cause)
