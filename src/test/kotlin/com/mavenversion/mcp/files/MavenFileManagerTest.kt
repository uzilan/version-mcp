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

@DisplayName("MavenFileManager Tests")
class MavenFileManagerTest {
    private lateinit var mavenFileManager: MavenFileManager

    @BeforeEach
    fun setUp() {
        mavenFileManager = MavenFileManager()
    }

    @Nested
    @DisplayName("POM File Reading")
    inner class PomFileReadingTests {
        @Test
        @DisplayName("Should read valid POM file successfully")
        fun shouldReadValidPomFileSuccessfully(
            @TempDir tempDir: Path,
        ) {
            val pomFile = tempDir.resolve("pom.xml").createFile()
            pomFile.writeText(createValidPomXml())

            val result = mavenFileManager.readPomFile(pomFile)

            assertThat(result.isSuccess).isTrue()
            val document = result.getOrThrow()
            assertThat(document.rootElement.name).isEqualTo("project")
            assertThat(document.rootElement.elementText("groupId")).isEqualTo("com.example")
            assertThat(document.rootElement.elementText("artifactId")).isEqualTo("test-project")
        }

        @Test
        @DisplayName("Should fail when POM file does not exist")
        fun shouldFailWhenPomFileDoesNotExist(
            @TempDir tempDir: Path,
        ) {
            val nonExistentPom = tempDir.resolve("nonexistent.xml")

            val result = mavenFileManager.readPomFile(nonExistentPom)

            assertThat(result.isFailure).isTrue()
            assertThat(result.exceptionOrNull()).isInstanceOf(MavenFileException::class.java)
            assertThat(result.exceptionOrNull()!!.message).contains("does not exist")
        }

        @Test
        @DisplayName("Should fail when POM has invalid structure")
        fun shouldFailWhenPomHasInvalidStructure(
            @TempDir tempDir: Path,
        ) {
            val pomFile = tempDir.resolve("pom.xml").createFile()
            pomFile.writeText(
                """
                <?xml version="1.0" encoding="UTF-8"?>
                <invalid-root>
                    <groupId>com.example</groupId>
                </invalid-root>
                """.trimIndent(),
            )

            val result = mavenFileManager.readPomFile(pomFile)

            assertThat(result.isFailure).isTrue()
            assertThat(result.exceptionOrNull()).isInstanceOf(MavenFileException::class.java)
            assertThat(result.exceptionOrNull()!!.message).contains("Root element must be 'project'")
        }

        @Test
        @DisplayName("Should fail when required elements are missing")
        fun shouldFailWhenRequiredElementsAreMissing(
            @TempDir tempDir: Path,
        ) {
            val pomFile = tempDir.resolve("pom.xml").createFile()
            pomFile.writeText(
                """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0">
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>com.example</groupId>
                    <!-- Missing artifactId and version -->
                </project>
                """.trimIndent(),
            )

            val result = mavenFileManager.readPomFile(pomFile)

            assertThat(result.isFailure).isTrue()
            assertThat(result.exceptionOrNull()).isInstanceOf(MavenFileException::class.java)
            assertThat(result.exceptionOrNull()!!.message).contains("Required element")
        }
    }

    @Nested
    @DisplayName("Dependency Extraction")
    inner class DependencyExtractionTests {
        @Test
        @DisplayName("Should extract dependencies from POM")
        fun shouldExtractDependenciesFromPom(
            @TempDir tempDir: Path,
        ) {
            val pomFile = tempDir.resolve("pom.xml").createFile()
            pomFile.writeText(createPomWithDependencies())

            val document = mavenFileManager.readPomFile(pomFile).getOrThrow()
            val dependencies = mavenFileManager.extractDependencies(document)

            assertThat(dependencies).hasSize(2)

            val junitDependency = dependencies.find { it.artifactId == "junit" }
            assertThat(junitDependency).isNotNull()
            assertThat(junitDependency!!.groupId).isEqualTo("junit")
            assertThat(junitDependency.version).isEqualTo("4.13.2")
            assertThat(junitDependency.scope).isEqualTo("test")

            val mockitoDependency = dependencies.find { it.artifactId == "mockito-core" }
            assertThat(mockitoDependency).isNotNull()
            assertThat(mockitoDependency!!.groupId).isEqualTo("org.mockito")
            assertThat(mockitoDependency.version).isEqualTo("4.6.1")
        }

        @Test
        @DisplayName("Should extract dependencies from dependencyManagement section")
        fun shouldExtractDependenciesFromDependencyManagement(
            @TempDir tempDir: Path,
        ) {
            val pomFile = tempDir.resolve("pom.xml").createFile()
            pomFile.writeText(createPomWithDependencyManagement())

            val document = mavenFileManager.readPomFile(pomFile).getOrThrow()
            val dependencies = mavenFileManager.extractDependencies(document)

            assertThat(dependencies).hasSize(1)

            val springDependency = dependencies.first()
            assertThat(springDependency.groupId).isEqualTo("org.springframework")
            assertThat(springDependency.artifactId).isEqualTo("spring-core")
            assertThat(springDependency.version).isEqualTo("5.3.21")
        }

        @Test
        @DisplayName("Should handle POM with no dependencies")
        fun shouldHandlePomWithNoDependencies(
            @TempDir tempDir: Path,
        ) {
            val pomFile = tempDir.resolve("pom.xml").createFile()
            pomFile.writeText(createValidPomXml())

            val document = mavenFileManager.readPomFile(pomFile).getOrThrow()
            val dependencies = mavenFileManager.extractDependencies(document)

            assertThat(dependencies).isEmpty()
        }
    }

    @Nested
    @DisplayName("Dependency Version Updates")
    inner class DependencyVersionUpdateTests {
        @Test
        @DisplayName("Should update dependency version successfully")
        fun shouldUpdateDependencyVersionSuccessfully(
            @TempDir tempDir: Path,
        ) {
            val pomFile = tempDir.resolve("pom.xml").createFile()
            pomFile.writeText(createPomWithDependencies())

            val document = mavenFileManager.readPomFile(pomFile).getOrThrow()
            val result = mavenFileManager.updateDependencyVersion(document, "junit", "junit", "4.14.0")

            assertThat(result.isSuccess).isTrue()
            val operationResult = result.getOrThrow()
            assertThat(operationResult.success).isTrue()
            assertThat(operationResult.message).contains("Successfully updated 1 dependency instances")
            assertThat(operationResult.updatedDependencies).hasSize(1)
            assertThat(operationResult.updatedDependencies.first().version).isEqualTo("4.14.0")
        }

        @Test
        @DisplayName("Should update multiple instances of same dependency")
        fun shouldUpdateMultipleInstancesOfSameDependency(
            @TempDir tempDir: Path,
        ) {
            val pomFile = tempDir.resolve("pom.xml").createFile()
            pomFile.writeText(createPomWithMultipleDependencyInstances())

            val document = mavenFileManager.readPomFile(pomFile).getOrThrow()
            val result = mavenFileManager.updateDependencyVersion(document, "junit", "junit", "4.14.0")

            assertThat(result.isSuccess).isTrue()
            val operationResult = result.getOrThrow()
            assertThat(operationResult.success).isTrue()
            assertThat(operationResult.message).contains("Successfully updated 2 dependency instances")
            assertThat(operationResult.updatedDependencies).hasSize(2)
        }

        @Test
        @DisplayName("Should fail when dependency not found")
        fun shouldFailWhenDependencyNotFound(
            @TempDir tempDir: Path,
        ) {
            val pomFile = tempDir.resolve("pom.xml").createFile()
            pomFile.writeText(createPomWithDependencies())

            val document = mavenFileManager.readPomFile(pomFile).getOrThrow()
            val result = mavenFileManager.updateDependencyVersion(document, "nonexistent", "dependency", "1.0.0")

            assertThat(result.isSuccess).isTrue()
            val operationResult = result.getOrThrow()
            assertThat(operationResult.success).isFalse()
            assertThat(operationResult.message).contains("not found in POM")
        }
    }

    @Nested
    @DisplayName("Dependency Addition")
    inner class DependencyAdditionTests {
        @Test
        @DisplayName("Should add new dependency successfully")
        fun shouldAddNewDependencySuccessfully(
            @TempDir tempDir: Path,
        ) {
            val pomFile = tempDir.resolve("pom.xml").createFile()
            pomFile.writeText(createValidPomXml())

            val document = mavenFileManager.readPomFile(pomFile).getOrThrow()
            val newDependency =
                MavenFileManager.MavenDependency(
                    groupId = "org.apache.commons",
                    artifactId = "commons-lang3",
                    version = "3.12.0",
                    scope = "compile",
                )

            val result = mavenFileManager.addDependency(document, newDependency)

            assertThat(result.isSuccess).isTrue()
            val operationResult = result.getOrThrow()
            assertThat(operationResult.success).isTrue()
            assertThat(operationResult.message).contains("Successfully added dependency")
            assertThat(operationResult.updatedDependencies).hasSize(1)
        }

        @Test
        @DisplayName("Should create dependencies section if it doesn't exist")
        fun shouldCreateDependenciesSectionIfItDoesntExist(
            @TempDir tempDir: Path,
        ) {
            val pomFile = tempDir.resolve("pom.xml").createFile()
            pomFile.writeText(createValidPomXml())

            val document = mavenFileManager.readPomFile(pomFile).getOrThrow()
            val newDependency =
                MavenFileManager.MavenDependency(
                    groupId = "org.apache.commons",
                    artifactId = "commons-lang3",
                    version = "3.12.0",
                )

            val result = mavenFileManager.addDependency(document, newDependency)

            assertThat(result.isSuccess).isTrue()
            val operationResult = result.getOrThrow()
            assertThat(operationResult.success).isTrue()

            // Verify dependencies section was created
            val dependenciesElement = document.rootElement.element("dependencies")
            assertThat(dependenciesElement).isNotNull()
        }

        @Test
        @DisplayName("Should fail when adding duplicate dependency")
        fun shouldFailWhenAddingDuplicateDependency(
            @TempDir tempDir: Path,
        ) {
            val pomFile = tempDir.resolve("pom.xml").createFile()
            pomFile.writeText(createPomWithDependencies())

            val document = mavenFileManager.readPomFile(pomFile).getOrThrow()
            val duplicateDependency =
                MavenFileManager.MavenDependency(
                    groupId = "junit",
                    artifactId = "junit",
                    version = "4.13.2",
                )

            val result = mavenFileManager.addDependency(document, duplicateDependency)

            assertThat(result.isSuccess).isTrue()
            val operationResult = result.getOrThrow()
            assertThat(operationResult.success).isFalse()
            assertThat(operationResult.message).contains("already exists")
        }
    }

    @Nested
    @DisplayName("POM File Writing")
    inner class PomFileWritingTests {
        @Test
        @DisplayName("Should write POM file successfully")
        fun shouldWritePomFileSuccessfully(
            @TempDir tempDir: Path,
        ) {
            val pomFile = tempDir.resolve("pom.xml").createFile()
            pomFile.writeText(createValidPomXml())

            val document = mavenFileManager.readPomFile(pomFile).getOrThrow()
            val result = mavenFileManager.writePomFile(document, pomFile, createBackup = false)

            assertThat(result.isSuccess).isTrue()
            val operationResult = result.getOrThrow()
            assertThat(operationResult.success).isTrue()
            assertThat(operationResult.message).contains("Successfully wrote POM file")
        }

        @Test
        @DisplayName("Should create backup when writing POM file")
        fun shouldCreateBackupWhenWritingPomFile(
            @TempDir tempDir: Path,
        ) {
            val pomFile = tempDir.resolve("pom.xml").createFile()
            pomFile.writeText(createValidPomXml())

            val document = mavenFileManager.readPomFile(pomFile).getOrThrow()
            val result = mavenFileManager.writePomFile(document, pomFile, createBackup = true)

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
            val pomFile = tempDir.resolve("nonexistent/pom.xml")
            val document =
                mavenFileManager.readPomFile(
                    tempDir.resolve("pom.xml").apply {
                        createFile().writeText(createValidPomXml())
                    },
                ).getOrThrow()

            val result = mavenFileManager.writePomFile(document, pomFile)

            assertThat(result.isFailure).isTrue()
            assertThat(result.exceptionOrNull()).isInstanceOf(MavenFileException::class.java)
            assertThat(result.exceptionOrNull()!!.message).contains("Parent directory does not exist")
        }
    }

    @Nested
    @DisplayName("MavenDependency Data Class")
    inner class MavenDependencyDataClassTests {
        @Test
        @DisplayName("Should create dependency coordinate correctly")
        fun shouldCreateDependencyCoordinateCorrectly() {
            val dependencyWithVersion =
                MavenFileManager.MavenDependency(
                    groupId = "junit",
                    artifactId = "junit",
                    version = "4.13.2",
                )
            assertThat(dependencyWithVersion.getCoordinate()).isEqualTo("junit:junit:4.13.2")

            val dependencyWithoutVersion =
                MavenFileManager.MavenDependency(
                    groupId = "junit",
                    artifactId = "junit",
                )
            assertThat(dependencyWithoutVersion.getCoordinate()).isEqualTo("junit:junit")
        }

        @Test
        @DisplayName("Should match dependencies correctly")
        fun shouldMatchDependenciesCorrectly() {
            val dependency1 =
                MavenFileManager.MavenDependency(
                    groupId = "junit",
                    artifactId = "junit",
                    version = "4.13.2",
                )
            val dependency2 =
                MavenFileManager.MavenDependency(
                    groupId = "junit",
                    artifactId = "junit",
                    version = "4.14.0",
                )
            val dependency3 =
                MavenFileManager.MavenDependency(
                    groupId = "org.mockito",
                    artifactId = "mockito-core",
                    version = "4.6.1",
                )

            assertThat(dependency1.matches(dependency2)).isTrue()
            assertThat(dependency1.matches(dependency3)).isFalse()
        }
    }

    // Helper methods for creating test POM files
    private fun createValidPomXml(): String {
        return """
            <?xml version="1.0" encoding="UTF-8"?>
            <project xmlns="http://maven.apache.org/POM/4.0.0"
                     xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                     xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 
                     http://maven.apache.org/xsd/maven-4.0.0.xsd">
                <modelVersion>4.0.0</modelVersion>
                <groupId>com.example</groupId>
                <artifactId>test-project</artifactId>
                <version>1.0.0</version>
                <packaging>jar</packaging>
            </project>
            """.trimIndent()
    }

    private fun createPomWithDependencies(): String {
        return """
            <?xml version="1.0" encoding="UTF-8"?>
            <project xmlns="http://maven.apache.org/POM/4.0.0"
                     xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                     xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 
                     http://maven.apache.org/xsd/maven-4.0.0.xsd">
                <modelVersion>4.0.0</modelVersion>
                <groupId>com.example</groupId>
                <artifactId>test-project</artifactId>
                <version>1.0.0</version>
                <packaging>jar</packaging>
                
                <dependencies>
                    <dependency>
                        <groupId>junit</groupId>
                        <artifactId>junit</artifactId>
                        <version>4.13.2</version>
                        <scope>test</scope>
                    </dependency>
                    <dependency>
                        <groupId>org.mockito</groupId>
                        <artifactId>mockito-core</artifactId>
                        <version>4.6.1</version>
                        <scope>test</scope>
                    </dependency>
                </dependencies>
            </project>
            """.trimIndent()
    }

    private fun createPomWithDependencyManagement(): String {
        return """
            <?xml version="1.0" encoding="UTF-8"?>
            <project xmlns="http://maven.apache.org/POM/4.0.0"
                     xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                     xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 
                     http://maven.apache.org/xsd/maven-4.0.0.xsd">
                <modelVersion>4.0.0</modelVersion>
                <groupId>com.example</groupId>
                <artifactId>test-project</artifactId>
                <version>1.0.0</version>
                <packaging>jar</packaging>
                
                <dependencyManagement>
                    <dependencies>
                        <dependency>
                            <groupId>org.springframework</groupId>
                            <artifactId>spring-core</artifactId>
                            <version>5.3.21</version>
                        </dependency>
                    </dependencies>
                </dependencyManagement>
            </project>
            """.trimIndent()
    }

    private fun createPomWithMultipleDependencyInstances(): String {
        return """
            <?xml version="1.0" encoding="UTF-8"?>
            <project xmlns="http://maven.apache.org/POM/4.0.0"
                     xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                     xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 
                     http://maven.apache.org/xsd/maven-4.0.0.xsd">
                <modelVersion>4.0.0</modelVersion>
                <groupId>com.example</groupId>
                <artifactId>test-project</artifactId>
                <version>1.0.0</version>
                <packaging>jar</packaging>
                
                <dependencies>
                    <dependency>
                        <groupId>junit</groupId>
                        <artifactId>junit</artifactId>
                        <version>4.13.2</version>
                        <scope>test</scope>
                    </dependency>
                </dependencies>
                
                <dependencyManagement>
                    <dependencies>
                        <dependency>
                            <groupId>junit</groupId>
                            <artifactId>junit</artifactId>
                            <version>4.13.2</version>
                        </dependency>
                    </dependencies>
                </dependencyManagement>
            </project>
            """.trimIndent()
    }
}
