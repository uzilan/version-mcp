package com.mavenversion.mcp.files

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.io.path.createFile
import kotlin.io.path.writeText

@DisplayName("GradleFileManager Tests")
class GradleFileManagerTest {
    private lateinit var gradleFileManager: GradleFileManager

    @BeforeEach
    fun setUp() {
        gradleFileManager = GradleFileManager()
    }

    @Nested
    @DisplayName("Gradle File Reading")
    inner class GradleFileReadingTests {
        @Test
        @DisplayName("Should read valid Groovy build.gradle file successfully")
        fun shouldReadValidGroovyBuildGradleFileSuccessfully(
            @TempDir tempDir: Path,
        ) {
            val buildFile = tempDir.resolve("build.gradle").createFile()
            buildFile.writeText(createValidGroovyBuildGradle())

            val result = gradleFileManager.readGradleFile(buildFile)

            assertThat(result.isSuccess).isTrue()
            val gradleContent = result.getOrThrow()
            assertThat(gradleContent.fileType).isEqualTo(GradleFileManager.GradleFileType.GROOVY)
            assertThat(gradleContent.content).contains("plugins {")
            assertThat(gradleContent.content).contains("group = 'com.example'")
        }

        @Test
        @DisplayName("Should read valid Kotlin DSL build.gradle.kts file successfully")
        fun shouldReadValidKotlinDslBuildGradleKtsFileSuccessfully(
            @TempDir tempDir: Path,
        ) {
            val buildFile = tempDir.resolve("build.gradle.kts").createFile()
            buildFile.writeText(createValidKotlinDslBuildGradle())

            val result = gradleFileManager.readGradleFile(buildFile)

            assertThat(result.isSuccess).isTrue()
            val gradleContent = result.getOrThrow()
            assertThat(gradleContent.fileType).isEqualTo(GradleFileManager.GradleFileType.KOTLIN_DSL)
            assertThat(gradleContent.content).contains("plugins {")
            assertThat(gradleContent.content).contains("group = \"com.example\"")
        }

        @Test
        @DisplayName("Should fail when Gradle file does not exist")
        fun shouldFailWhenGradleFileDoesNotExist(
            @TempDir tempDir: Path,
        ) {
            val nonExistentFile = tempDir.resolve("nonexistent.gradle")

            val result = gradleFileManager.readGradleFile(nonExistentFile)

            assertThat(result.isFailure).isTrue()
            assertThat(result.exceptionOrNull()).isInstanceOf(GradleFileException::class.java)
            assertThat(result.exceptionOrNull()!!.message).contains("does not exist")
        }

        @Test
        @DisplayName("Should fail when Gradle file has invalid structure")
        fun shouldFailWhenGradleFileHasInvalidStructure(
            @TempDir tempDir: Path,
        ) {
            val buildFile = tempDir.resolve("build.gradle").createFile()
            buildFile.writeText(
                """
                // This is not a valid Gradle file
                some random content
                without proper structure
                """.trimIndent(),
            )

            val result = gradleFileManager.readGradleFile(buildFile)

            assertThat(result.isFailure).isTrue()
            assertThat(result.exceptionOrNull()).isInstanceOf(GradleFileException::class.java)
            assertThat(result.exceptionOrNull()!!.message).contains("Invalid Groovy Gradle file")
        }
    }

    @Nested
    @DisplayName("Dependency Extraction - Groovy")
    inner class DependencyExtractionGroovyTests {
        @Test
        @DisplayName("Should extract dependencies from Groovy build.gradle")
        fun shouldExtractDependenciesFromGroovyBuildGradle(
            @TempDir tempDir: Path,
        ) {
            val buildFile = tempDir.resolve("build.gradle").createFile()
            buildFile.writeText(createGroovyBuildGradleWithDependencies())

            val gradleContent = gradleFileManager.readGradleFile(buildFile).getOrThrow()
            val dependencies = gradleFileManager.extractDependencies(gradleContent)

            assertThat(dependencies).hasSize(3)

            val junitDependency = dependencies.find { it.artifactId == "junit" }
            assertThat(junitDependency).isNotNull()
            assertThat(junitDependency!!.groupId).isEqualTo("junit")
            assertThat(junitDependency.version).isEqualTo("4.13.2")
            assertThat(junitDependency.configuration).isEqualTo("testImplementation")

            val mockitoDependency = dependencies.find { it.artifactId == "mockito-core" }
            assertThat(mockitoDependency).isNotNull()
            assertThat(mockitoDependency!!.groupId).isEqualTo("org.mockito")
            assertThat(mockitoDependency.version).isEqualTo("4.6.1")
            assertThat(mockitoDependency.configuration).isEqualTo("testImplementation")

            val springDependency = dependencies.find { it.artifactId == "spring-core" }
            assertThat(springDependency).isNotNull()
            assertThat(springDependency!!.groupId).isEqualTo("org.springframework")
            assertThat(springDependency.version).isEqualTo("5.3.21")
            assertThat(springDependency.configuration).isEqualTo("implementation")
        }

        @Test
        @DisplayName("Should handle Groovy build.gradle with no dependencies")
        fun shouldHandleGroovyBuildGradleWithNoDependencies(
            @TempDir tempDir: Path,
        ) {
            val buildFile = tempDir.resolve("build.gradle").createFile()
            buildFile.writeText(createValidGroovyBuildGradle())

            val gradleContent = gradleFileManager.readGradleFile(buildFile).getOrThrow()
            val dependencies = gradleFileManager.extractDependencies(gradleContent)

            assertThat(dependencies).isEmpty()
        }
    }

    @Nested
    @DisplayName("Dependency Extraction - Kotlin DSL")
    inner class DependencyExtractionKotlinDslTests {
        @Test
        @DisplayName("Should extract dependencies from Kotlin DSL build.gradle.kts")
        fun shouldExtractDependenciesFromKotlinDslBuildGradleKts(
            @TempDir tempDir: Path,
        ) {
            val buildFile = tempDir.resolve("build.gradle.kts").createFile()
            buildFile.writeText(createKotlinDslBuildGradleWithDependencies())

            val gradleContent = gradleFileManager.readGradleFile(buildFile).getOrThrow()
            val dependencies = gradleFileManager.extractDependencies(gradleContent)

            assertThat(dependencies).hasSize(3)

            val junitDependency = dependencies.find { it.artifactId == "junit" }
            assertThat(junitDependency).isNotNull()
            assertThat(junitDependency!!.groupId).isEqualTo("junit")
            assertThat(junitDependency.version).isEqualTo("4.13.2")
            assertThat(junitDependency.configuration).isEqualTo("testImplementation")

            val mockitoDependency = dependencies.find { it.artifactId == "mockito-core" }
            assertThat(mockitoDependency).isNotNull()
            assertThat(mockitoDependency!!.groupId).isEqualTo("org.mockito")
            assertThat(mockitoDependency.version).isEqualTo("4.6.1")
            assertThat(mockitoDependency.configuration).isEqualTo("testImplementation")

            val springDependency = dependencies.find { it.artifactId == "spring-core" }
            assertThat(springDependency).isNotNull()
            assertThat(springDependency!!.groupId).isEqualTo("org.springframework")
            assertThat(springDependency.version).isEqualTo("5.3.21")
            assertThat(springDependency.configuration).isEqualTo("implementation")
        }

        @Test
        @DisplayName("Should handle Kotlin DSL build.gradle.kts with no dependencies")
        fun shouldHandleKotlinDslBuildGradleKtsWithNoDependencies(
            @TempDir tempDir: Path,
        ) {
            val buildFile = tempDir.resolve("build.gradle.kts").createFile()
            buildFile.writeText(createValidKotlinDslBuildGradle())

            val gradleContent = gradleFileManager.readGradleFile(buildFile).getOrThrow()
            val dependencies = gradleFileManager.extractDependencies(gradleContent)

            assertThat(dependencies).isEmpty()
        }
    }

    @Nested
    @DisplayName("Dependency Version Updates - Groovy")
    inner class DependencyVersionUpdateGroovyTests {
        @Test
        @DisplayName("Should update dependency version in Groovy build.gradle successfully")
        fun shouldUpdateDependencyVersionInGroovyBuildGradleSuccessfully(
            @TempDir tempDir: Path,
        ) {
            val buildFile = tempDir.resolve("build.gradle").createFile()
            buildFile.writeText(createGroovyBuildGradleWithDependencies())

            val gradleContent = gradleFileManager.readGradleFile(buildFile).getOrThrow()
            val result = gradleFileManager.updateDependencyVersion(gradleContent, "junit", "junit", "4.14.0")

            assertThat(result.isSuccess).isTrue()
            val operationResult = result.getOrThrow()
            assertThat(operationResult.success).isTrue()
            assertThat(operationResult.message).contains("Successfully updated dependency junit:junit to version 4.14.0")
            assertThat(operationResult.updatedDependencies).hasSize(1)
            assertThat(operationResult.updatedDependencies.first().version).isEqualTo("4.14.0")
        }

        @Test
        @DisplayName("Should fail when dependency not found in Groovy build.gradle")
        fun shouldFailWhenDependencyNotFoundInGroovyBuildGradle(
            @TempDir tempDir: Path,
        ) {
            val buildFile = tempDir.resolve("build.gradle").createFile()
            buildFile.writeText(createGroovyBuildGradleWithDependencies())

            val gradleContent = gradleFileManager.readGradleFile(buildFile).getOrThrow()
            val result = gradleFileManager.updateDependencyVersion(gradleContent, "nonexistent", "dependency", "1.0.0")

            assertThat(result.isSuccess).isTrue()
            val operationResult = result.getOrThrow()
            assertThat(operationResult.success).isFalse()
            assertThat(operationResult.message).contains("not found in Gradle file")
        }
    }

    @Nested
    @DisplayName("Dependency Version Updates - Kotlin DSL")
    inner class DependencyVersionUpdateKotlinDslTests {
        @Test
        @DisplayName("Should update dependency version in Kotlin DSL build.gradle.kts successfully")
        fun shouldUpdateDependencyVersionInKotlinDslBuildGradleKtsSuccessfully(
            @TempDir tempDir: Path,
        ) {
            val buildFile = tempDir.resolve("build.gradle.kts").createFile()
            buildFile.writeText(createKotlinDslBuildGradleWithDependencies())

            val gradleContent = gradleFileManager.readGradleFile(buildFile).getOrThrow()
            val result = gradleFileManager.updateDependencyVersion(gradleContent, "junit", "junit", "4.14.0")

            assertThat(result.isSuccess).isTrue()
            val operationResult = result.getOrThrow()
            assertThat(operationResult.success).isTrue()
            assertThat(operationResult.message).contains("Successfully updated dependency junit:junit to version 4.14.0")
            assertThat(operationResult.updatedDependencies).hasSize(1)
            assertThat(operationResult.updatedDependencies.first().version).isEqualTo("4.14.0")
        }

        @Test
        @DisplayName("Should fail when dependency not found in Kotlin DSL build.gradle.kts")
        fun shouldFailWhenDependencyNotFoundInKotlinDslBuildGradleKts(
            @TempDir tempDir: Path,
        ) {
            val buildFile = tempDir.resolve("build.gradle.kts").createFile()
            buildFile.writeText(createKotlinDslBuildGradleWithDependencies())

            val gradleContent = gradleFileManager.readGradleFile(buildFile).getOrThrow()
            val result = gradleFileManager.updateDependencyVersion(gradleContent, "nonexistent", "dependency", "1.0.0")

            assertThat(result.isSuccess).isTrue()
            val operationResult = result.getOrThrow()
            assertThat(operationResult.success).isFalse()
            assertThat(operationResult.message).contains("not found in Gradle file")
        }
    }

    @Nested
    @DisplayName("Dependency Addition - Groovy")
    inner class DependencyAdditionGroovyTests {
        @Test
        @DisplayName("Should add new dependency to Groovy build.gradle successfully")
        fun shouldAddNewDependencyToGroovyBuildGradleSuccessfully(
            @TempDir tempDir: Path,
        ) {
            val buildFile = tempDir.resolve("build.gradle").createFile()
            buildFile.writeText(createValidGroovyBuildGradle())

            val gradleContent = gradleFileManager.readGradleFile(buildFile).getOrThrow()
            val newDependency =
                GradleFileManager.GradleDependency(
                    configuration = "implementation",
                    groupId = "org.apache.commons",
                    artifactId = "commons-lang3",
                    version = "3.12.0",
                )

            val result = gradleFileManager.addDependency(gradleContent, newDependency)

            assertThat(result.isSuccess).isTrue()
            val operationResult = result.getOrThrow()
            assertThat(operationResult.success).isTrue()
            assertThat(operationResult.message).contains("Successfully added dependency")
            assertThat(operationResult.updatedDependencies).hasSize(1)
        }

        @Test
        @DisplayName("Should create dependencies block if it doesn't exist in Groovy build.gradle")
        fun shouldCreateDependenciesBlockIfItDoesntExistInGroovyBuildGradle(
            @TempDir tempDir: Path,
        ) {
            val buildFile = tempDir.resolve("build.gradle").createFile()
            buildFile.writeText(
                """
                plugins {
                    id 'java'
                }
                """.trimIndent(),
            )

            val gradleContent = gradleFileManager.readGradleFile(buildFile).getOrThrow()
            val newDependency =
                GradleFileManager.GradleDependency(
                    configuration = "implementation",
                    groupId = "org.apache.commons",
                    artifactId = "commons-lang3",
                    version = "3.12.0",
                )

            val result = gradleFileManager.addDependency(gradleContent, newDependency)

            assertThat(result.isSuccess).isTrue()
            val operationResult = result.getOrThrow()
            assertThat(operationResult.success).isTrue()
        }

        @Test
        @DisplayName("Should fail when adding duplicate dependency to Groovy build.gradle")
        fun shouldFailWhenAddingDuplicateDependencyToGroovyBuildGradle(
            @TempDir tempDir: Path,
        ) {
            val buildFile = tempDir.resolve("build.gradle").createFile()
            buildFile.writeText(createGroovyBuildGradleWithDependencies())

            val gradleContent = gradleFileManager.readGradleFile(buildFile).getOrThrow()
            val duplicateDependency =
                GradleFileManager.GradleDependency(
                    configuration = "testImplementation",
                    groupId = "junit",
                    artifactId = "junit",
                    version = "4.13.2",
                )

            val result = gradleFileManager.addDependency(gradleContent, duplicateDependency)

            assertThat(result.isSuccess).isTrue()
            val operationResult = result.getOrThrow()
            assertThat(operationResult.success).isFalse()
            assertThat(operationResult.message).contains("already exists")
        }
    }

    @Nested
    @DisplayName("Dependency Addition - Kotlin DSL")
    inner class DependencyAdditionKotlinDslTests {
        @Test
        @DisplayName("Should add new dependency to Kotlin DSL build.gradle.kts successfully")
        fun shouldAddNewDependencyToKotlinDslBuildGradleKtsSuccessfully(
            @TempDir tempDir: Path,
        ) {
            val buildFile = tempDir.resolve("build.gradle.kts").createFile()
            buildFile.writeText(createValidKotlinDslBuildGradle())

            val gradleContent = gradleFileManager.readGradleFile(buildFile).getOrThrow()
            val newDependency =
                GradleFileManager.GradleDependency(
                    configuration = "implementation",
                    groupId = "org.apache.commons",
                    artifactId = "commons-lang3",
                    version = "3.12.0",
                )

            val result = gradleFileManager.addDependency(gradleContent, newDependency)

            assertThat(result.isSuccess).isTrue()
            val operationResult = result.getOrThrow()
            assertThat(operationResult.success).isTrue()
            assertThat(operationResult.message).contains("Successfully added dependency")
            assertThat(operationResult.updatedDependencies).hasSize(1)
        }

        @Test
        @DisplayName("Should create dependencies block if it doesn't exist in Kotlin DSL build.gradle.kts")
        fun shouldCreateDependenciesBlockIfItDoesntExistInKotlinDslBuildGradleKts(
            @TempDir tempDir: Path,
        ) {
            val buildFile = tempDir.resolve("build.gradle.kts").createFile()
            buildFile.writeText(
                """
                plugins {
                    java
                }
                """.trimIndent(),
            )

            val gradleContent = gradleFileManager.readGradleFile(buildFile).getOrThrow()
            val newDependency =
                GradleFileManager.GradleDependency(
                    configuration = "implementation",
                    groupId = "org.apache.commons",
                    artifactId = "commons-lang3",
                    version = "3.12.0",
                )

            val result = gradleFileManager.addDependency(gradleContent, newDependency)

            assertThat(result.isSuccess).isTrue()
            val operationResult = result.getOrThrow()
            assertThat(operationResult.success).isTrue()
        }

        @Test
        @DisplayName("Should fail when adding duplicate dependency to Kotlin DSL build.gradle.kts")
        fun shouldFailWhenAddingDuplicateDependencyToKotlinDslBuildGradleKts(
            @TempDir tempDir: Path,
        ) {
            val buildFile = tempDir.resolve("build.gradle.kts").createFile()
            buildFile.writeText(createKotlinDslBuildGradleWithDependencies())

            val gradleContent = gradleFileManager.readGradleFile(buildFile).getOrThrow()
            val duplicateDependency =
                GradleFileManager.GradleDependency(
                    configuration = "testImplementation",
                    groupId = "junit",
                    artifactId = "junit",
                    version = "4.13.2",
                )

            val result = gradleFileManager.addDependency(gradleContent, duplicateDependency)

            assertThat(result.isSuccess).isTrue()
            val operationResult = result.getOrThrow()
            assertThat(operationResult.success).isFalse()
            assertThat(operationResult.message).contains("already exists")
        }
    }

    @Nested
    @DisplayName("Gradle File Writing")
    inner class GradleFileWritingTests {
        @Test
        @DisplayName("Should write Gradle file successfully")
        fun shouldWriteGradleFileSuccessfully(
            @TempDir tempDir: Path,
        ) {
            val buildFile = tempDir.resolve("build.gradle").createFile()
            buildFile.writeText(createValidGroovyBuildGradle())

            val gradleContent = gradleFileManager.readGradleFile(buildFile).getOrThrow()
            val result = gradleFileManager.writeGradleFile(gradleContent, buildFile, createBackup = false)

            assertThat(result.isSuccess).isTrue()
            val operationResult = result.getOrThrow()
            assertThat(operationResult.success).isTrue()
            assertThat(operationResult.message).contains("Successfully wrote Gradle file")
        }

        @Test
        @DisplayName("Should create backup when writing Gradle file")
        fun shouldCreateBackupWhenWritingGradleFile(
            @TempDir tempDir: Path,
        ) {
            val buildFile = tempDir.resolve("build.gradle").createFile()
            buildFile.writeText(createValidGroovyBuildGradle())

            val gradleContent = gradleFileManager.readGradleFile(buildFile).getOrThrow()
            val result = gradleFileManager.writeGradleFile(gradleContent, buildFile, createBackup = true)

            assertThat(result.isSuccess).isTrue()
            val operationResult = result.getOrThrow()
            assertThat(operationResult.success).isTrue()
            assertThat(operationResult.backupPath).isNotNull()
            assertThat(operationResult.backupPath!!.toFile().exists()).isTrue()
        }

        @Test
        @DisplayName("Should fail when parent directory does not exist")
        fun shouldFailWhenParentDirectoryDoesNotExist(
            @TempDir tempDir: Path,
        ) {
            val buildFile = tempDir.resolve("nonexistent/build.gradle")
            val gradleContent =
                gradleFileManager.readGradleFile(
                    tempDir.resolve("build.gradle").apply {
                        createFile().writeText(createValidGroovyBuildGradle())
                    },
                ).getOrThrow()

            val result = gradleFileManager.writeGradleFile(gradleContent, buildFile)

            assertThat(result.isFailure).isTrue()
            assertThat(result.exceptionOrNull()).isInstanceOf(GradleFileException::class.java)
            assertThat(result.exceptionOrNull()!!.message).contains("Parent directory does not exist")
        }
    }

    @Nested
    @DisplayName("GradleDependency Data Class")
    inner class GradleDependencyDataClassTests {
        @Test
        @DisplayName("Should create dependency coordinate correctly")
        fun shouldCreateDependencyCoordinateCorrectly() {
            val dependencyWithVersion =
                GradleFileManager.GradleDependency(
                    configuration = "implementation",
                    groupId = "junit",
                    artifactId = "junit",
                    version = "4.13.2",
                )
            assertThat(dependencyWithVersion.getCoordinate()).isEqualTo("junit:junit:4.13.2")

            val dependencyWithoutVersion =
                GradleFileManager.GradleDependency(
                    configuration = "implementation",
                    groupId = "junit",
                    artifactId = "junit",
                )
            assertThat(dependencyWithoutVersion.getCoordinate()).isEqualTo("junit:junit")
        }

        @Test
        @DisplayName("Should match dependencies correctly")
        fun shouldMatchDependenciesCorrectly() {
            val dependency1 =
                GradleFileManager.GradleDependency(
                    configuration = "implementation",
                    groupId = "junit",
                    artifactId = "junit",
                    version = "4.13.2",
                )
            val dependency2 =
                GradleFileManager.GradleDependency(
                    configuration = "testImplementation",
                    groupId = "junit",
                    artifactId = "junit",
                    version = "4.14.0",
                )
            val dependency3 =
                GradleFileManager.GradleDependency(
                    configuration = "implementation",
                    groupId = "org.mockito",
                    artifactId = "mockito-core",
                    version = "4.6.1",
                )

            assertThat(dependency1.matches(dependency2)).isTrue()
            assertThat(dependency1.matches(dependency3)).isFalse()
        }
    }

    // Helper methods for creating test Gradle files
    private fun createValidGroovyBuildGradle(): String {
        return """
            plugins {
                id 'java'
            }
            
            group = 'com.example'
            version = '1.0.0'
            """.trimIndent()
    }

    private fun createValidKotlinDslBuildGradle(): String {
        return """
            plugins {
                java
            }
            
            group = "com.example"
            version = "1.0.0"
            """.trimIndent()
    }

    private fun createGroovyBuildGradleWithDependencies(): String {
        return """
            plugins {
                id 'java'
            }
            
            group = 'com.example'
            version = '1.0.0'
            
            dependencies {
                implementation 'org.springframework:spring-core:5.3.21'
                testImplementation 'junit:junit:4.13.2'
                testImplementation 'org.mockito:mockito-core:4.6.1'
            }
            """.trimIndent()
    }

    private fun createKotlinDslBuildGradleWithDependencies(): String {
        return """
            plugins {
                java
            }
            
            group = "com.example"
            version = "1.0.0"
            
            dependencies {
                implementation("org.springframework:spring-core:5.3.21")
                testImplementation("junit:junit:4.13.2")
                testImplementation("org.mockito:mockito-core:4.6.1")
            }
            """.trimIndent()
    }
}
