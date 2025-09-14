package com.mavenversion.mcp.files

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createFile
import kotlin.io.path.writeText

@DisplayName("ProjectFileDetector Tests")
class ProjectFileDetectorTest {
    private lateinit var detector: ProjectFileDetector

    @BeforeEach
    fun setUp() {
        detector = ProjectFileDetector()
    }

    @Nested
    @DisplayName("Maven Project Detection")
    inner class MavenProjectDetectionTests {
        @Test
        @DisplayName("Should detect Maven project with pom.xml")
        fun shouldDetectMavenProjectWithPomXml(
            @TempDir tempDir: Path,
        ) {
            // Create a pom.xml file
            val pomFile = tempDir.resolve("pom.xml").createFile()
            pomFile.writeText(
                """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0">
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>com.example</groupId>
                    <artifactId>test-project</artifactId>
                    <version>1.0.0</version>
                </project>
                """.trimIndent(),
            )

            val result = detector.detectProject(tempDir.toString())

            assertThat(result.isSuccess).isTrue()
            val projectInfo = result.getOrThrow()
            assertThat(projectInfo.type).isEqualTo(ProjectFileDetector.ProjectType.MAVEN)
            assertThat(projectInfo.buildFiles).hasSize(1)
            assertThat(projectInfo.buildFiles[0].type).isEqualTo(ProjectFileDetector.BuildFileType.MAVEN_POM)
            assertThat(projectInfo.buildFiles[0].isReadable).isTrue()
            assertThat(projectInfo.buildFiles[0].isWritable).isTrue()
        }

        @Test
        @DisplayName("Should detect multi-module Maven project")
        fun shouldDetectMultiModuleMavenProject(
            @TempDir tempDir: Path,
        ) {
            // Create root pom.xml
            tempDir.resolve("pom.xml").createFile().writeText("<project></project>")

            // Create module directories with pom.xml files
            val module1Dir = Files.createDirectory(tempDir.resolve("module1"))
            module1Dir.resolve("pom.xml").createFile().writeText("<project></project>")

            val module2Dir = Files.createDirectory(tempDir.resolve("module2"))
            module2Dir.resolve("pom.xml").createFile().writeText("<project></project>")

            val result = detector.detectProject(tempDir.toString())

            assertThat(result.isSuccess).isTrue()
            val projectInfo = result.getOrThrow()
            assertThat(projectInfo.type).isEqualTo(ProjectFileDetector.ProjectType.MAVEN)
            assertThat(projectInfo.buildFiles).hasSize(3) // Root + 2 modules

            val pomFiles = detector.getBuildFilesByType(projectInfo, ProjectFileDetector.BuildFileType.MAVEN_POM)
            assertThat(pomFiles).hasSize(3)
        }
    }

    @Nested
    @DisplayName("Gradle Project Detection")
    inner class GradleProjectDetectionTests {
        @Test
        @DisplayName("Should detect Gradle project with build.gradle")
        fun shouldDetectGradleProjectWithBuildGradle(
            @TempDir tempDir: Path,
        ) {
            val buildFile = tempDir.resolve("build.gradle").createFile()
            buildFile.writeText(
                """
                plugins {
                    id 'java'
                }
                
                dependencies {
                    implementation 'junit:junit:4.13.2'
                }
                """.trimIndent(),
            )

            val result = detector.detectProject(tempDir.toString())

            assertThat(result.isSuccess).isTrue()
            val projectInfo = result.getOrThrow()
            assertThat(projectInfo.type).isEqualTo(ProjectFileDetector.ProjectType.GRADLE)
            assertThat(projectInfo.buildFiles).hasSize(1)
            assertThat(projectInfo.buildFiles[0].type).isEqualTo(ProjectFileDetector.BuildFileType.GRADLE_GROOVY)
        }

        @Test
        @DisplayName("Should detect Gradle project with build.gradle.kts")
        fun shouldDetectGradleProjectWithBuildGradleKts(
            @TempDir tempDir: Path,
        ) {
            val buildFile = tempDir.resolve("build.gradle.kts").createFile()
            buildFile.writeText(
                """
                plugins {
                    kotlin("jvm") version "1.9.0"
                }
                
                dependencies {
                    implementation("junit:junit:4.13.2")
                }
                """.trimIndent(),
            )

            val result = detector.detectProject(tempDir.toString())

            assertThat(result.isSuccess).isTrue()
            val projectInfo = result.getOrThrow()
            assertThat(projectInfo.type).isEqualTo(ProjectFileDetector.ProjectType.GRADLE)
            assertThat(projectInfo.buildFiles).hasSize(1)
            assertThat(projectInfo.buildFiles[0].type).isEqualTo(ProjectFileDetector.BuildFileType.GRADLE_KOTLIN)
        }

        @Test
        @DisplayName("Should prefer Kotlin DSL over Groovy when both exist")
        fun shouldPreferKotlinDslOverGroovy(
            @TempDir tempDir: Path,
        ) {
            tempDir.resolve("build.gradle").createFile().writeText("// Groovy build")
            tempDir.resolve("build.gradle.kts").createFile().writeText("// Kotlin build")

            val result = detector.detectProject(tempDir.toString())

            assertThat(result.isSuccess).isTrue()
            val projectInfo = result.getOrThrow()
            assertThat(projectInfo.type).isEqualTo(ProjectFileDetector.ProjectType.GRADLE)
            assertThat(projectInfo.buildFiles).hasSize(2)

            val primaryBuildFile = detector.findPrimaryBuildFile(projectInfo)
            assertThat(primaryBuildFile).isNotNull()
            assertThat(primaryBuildFile!!.type).isEqualTo(ProjectFileDetector.BuildFileType.GRADLE_KOTLIN)
        }
    }

    @Nested
    @DisplayName("Mixed Project Detection")
    inner class MixedProjectDetectionTests {
        @Test
        @DisplayName("Should detect mixed Maven/Gradle project")
        fun shouldDetectMixedMavenGradleProject(
            @TempDir tempDir: Path,
        ) {
            tempDir.resolve("pom.xml").createFile().writeText("<project></project>")
            tempDir.resolve("build.gradle").createFile().writeText("// Gradle build")

            val result = detector.detectProject(tempDir.toString())

            assertThat(result.isSuccess).isTrue()
            val projectInfo = result.getOrThrow()
            assertThat(projectInfo.type).isEqualTo(ProjectFileDetector.ProjectType.MIXED)
            assertThat(projectInfo.buildFiles).hasSize(2)

            // Should prefer Maven for mixed projects
            val primaryBuildFile = detector.findPrimaryBuildFile(projectInfo)
            assertThat(primaryBuildFile).isNotNull()
            assertThat(primaryBuildFile!!.type).isEqualTo(ProjectFileDetector.BuildFileType.MAVEN_POM)
        }
    }

    @Nested
    @DisplayName("Unknown Project Detection")
    inner class UnknownProjectDetectionTests {
        @Test
        @DisplayName("Should detect unknown project when no build files exist")
        fun shouldDetectUnknownProjectWhenNoBuildFiles(
            @TempDir tempDir: Path,
        ) {
            // Create some non-build files
            tempDir.resolve("README.md").createFile().writeText("# Test Project")
            tempDir.resolve("src").let { Files.createDirectory(it) }

            val result = detector.detectProject(tempDir.toString())

            assertThat(result.isSuccess).isTrue()
            val projectInfo = result.getOrThrow()
            assertThat(projectInfo.type).isEqualTo(ProjectFileDetector.ProjectType.UNKNOWN)
            assertThat(projectInfo.buildFiles).isEmpty()
        }
    }

    @Nested
    @DisplayName("Error Handling")
    inner class ErrorHandlingTests {
        @Test
        @DisplayName("Should fail when project path does not exist")
        fun shouldFailWhenProjectPathDoesNotExist() {
            val result = detector.detectProject("/nonexistent/path")

            assertThat(result.isFailure).isTrue()
            assertThat(result.exceptionOrNull()).isInstanceOf(ProjectDetectionException::class.java)
            assertThat(result.exceptionOrNull()!!.message).contains("does not exist")
        }

        @Test
        @DisplayName("Should fail when project path is not a directory")
        fun shouldFailWhenProjectPathIsNotDirectory(
            @TempDir tempDir: Path,
        ) {
            val file = tempDir.resolve("not-a-directory.txt").createFile()

            val result = detector.detectProject(file.toString())

            assertThat(result.isFailure).isTrue()
            assertThat(result.exceptionOrNull()).isInstanceOf(ProjectDetectionException::class.java)
            assertThat(result.exceptionOrNull()!!.message).contains("not a directory")
        }
    }

    @Nested
    @DisplayName("Build File Access Validation")
    inner class BuildFileAccessValidationTests {
        @Test
        @DisplayName("Should validate accessible build file")
        fun shouldValidateAccessibleBuildFile(
            @TempDir tempDir: Path,
        ) {
            val pomFile = tempDir.resolve("pom.xml").createFile()
            pomFile.writeText("<project></project>")

            val projectInfo = detector.detectProject(tempDir.toString()).getOrThrow()
            val buildFile = projectInfo.buildFiles[0]

            val result = detector.validateBuildFileAccess(buildFile)

            assertThat(result.isSuccess).isTrue()
        }

        @Test
        @DisplayName("Should fail validation for non-existent build file")
        fun shouldFailValidationForNonExistentBuildFile() {
            val nonExistentFile =
                ProjectFileDetector.BuildFile(
                    path = Path.of("/nonexistent/pom.xml"),
                    type = ProjectFileDetector.BuildFileType.MAVEN_POM,
                    isReadable = false,
                    isWritable = false,
                    exists = false,
                )

            val result = detector.validateBuildFileAccess(nonExistentFile)

            assertThat(result.isFailure).isTrue()
            assertThat(result.exceptionOrNull()).isInstanceOf(ProjectDetectionException::class.java)
            assertThat(result.exceptionOrNull()!!.message).contains("does not exist")
        }
    }

    @Nested
    @DisplayName("Primary Build File Selection")
    inner class PrimaryBuildFileSelectionTests {
        @Test
        @DisplayName("Should select root pom.xml over subdirectory pom.xml")
        fun shouldSelectRootPomOverSubdirectoryPom(
            @TempDir tempDir: Path,
        ) {
            // Create root pom.xml
            tempDir.resolve("pom.xml").createFile().writeText("<project></project>")

            // Create subdirectory pom.xml
            val subDir = Files.createDirectory(tempDir.resolve("module"))
            subDir.resolve("pom.xml").createFile().writeText("<project></project>")

            val projectInfo = detector.detectProject(tempDir.toString()).getOrThrow()
            val primaryBuildFile = detector.findPrimaryBuildFile(projectInfo)

            assertThat(primaryBuildFile).isNotNull()
            assertThat(primaryBuildFile!!.path.fileName.toString()).isEqualTo("pom.xml")
            assertThat(primaryBuildFile.path.parent).isEqualTo(tempDir)
        }

        @Test
        @DisplayName("Should return null for unknown project type")
        fun shouldReturnNullForUnknownProjectType(
            @TempDir tempDir: Path,
        ) {
            val projectInfo = detector.detectProject(tempDir.toString()).getOrThrow()
            val primaryBuildFile = detector.findPrimaryBuildFile(projectInfo)

            assertThat(primaryBuildFile).isNull()
        }
    }

    @Nested
    @DisplayName("Utility Methods")
    inner class UtilityMethodsTests {
        @Test
        @DisplayName("Should detect multiple build files of same type")
        fun shouldDetectMultipleBuildFilesOfSameType(
            @TempDir tempDir: Path,
        ) {
            // Create multiple pom.xml files
            tempDir.resolve("pom.xml").createFile().writeText("<project></project>")

            val subDir = Files.createDirectory(tempDir.resolve("module"))
            subDir.resolve("pom.xml").createFile().writeText("<project></project>")

            val projectInfo = detector.detectProject(tempDir.toString()).getOrThrow()
            val hasMultiple = detector.hasMultipleBuildFiles(projectInfo, ProjectFileDetector.BuildFileType.MAVEN_POM)

            assertThat(hasMultiple).isTrue()
        }

        @Test
        @DisplayName("Should get build files by type")
        fun shouldGetBuildFilesByType(
            @TempDir tempDir: Path,
        ) {
            tempDir.resolve("pom.xml").createFile().writeText("<project></project>")
            tempDir.resolve("build.gradle").createFile().writeText("// Gradle")

            val projectInfo = detector.detectProject(tempDir.toString()).getOrThrow()
            val mavenFiles = detector.getBuildFilesByType(projectInfo, ProjectFileDetector.BuildFileType.MAVEN_POM)
            val gradleFiles = detector.getBuildFilesByType(projectInfo, ProjectFileDetector.BuildFileType.GRADLE_GROOVY)

            assertThat(mavenFiles).hasSize(1)
            assertThat(gradleFiles).hasSize(1)
        }
    }
}
