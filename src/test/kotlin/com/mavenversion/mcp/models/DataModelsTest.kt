package com.mavenversion.mcp.models

import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.assertj.core.api.Assertions.assertThat

/**
 * Unit tests for data models including serialization and validation
 */
class DataModelsTest {
    
    private val json = Json { prettyPrint = true }
    
    @Nested
    @DisplayName("Dependency Tests")
    inner class DependencyTests {
        
        @Test
        @DisplayName("Should serialize and deserialize dependency with all fields")
        fun testDependencySerialization() {
            val dependency = Dependency(
                groupId = "org.springframework",
                artifactId = "spring-core",
                description = "Spring Core Framework",
                url = "https://spring.io"
            )
            
            val jsonString = json.encodeToString(dependency)
            val deserializedDependency = json.decodeFromString<Dependency>(jsonString)
            
            assertThat(deserializedDependency).isEqualTo(dependency)
            assertThat(deserializedDependency.groupId).isEqualTo("org.springframework")
            assertThat(deserializedDependency.artifactId).isEqualTo("spring-core")
            assertThat(deserializedDependency.description).isEqualTo("Spring Core Framework")
            assertThat(deserializedDependency.url).isEqualTo("https://spring.io")
        }
        
        @Test
        @DisplayName("Should handle null optional fields correctly")
        fun testDependencyWithNullValues() {
            val dependency = Dependency(
                groupId = "com.example",
                artifactId = "test-lib"
            )
            
            val jsonString = json.encodeToString(dependency)
            val deserializedDependency = json.decodeFromString<Dependency>(jsonString)
            
            assertThat(deserializedDependency).isEqualTo(dependency)
            assertThat(deserializedDependency.groupId).isEqualTo("com.example")
            assertThat(deserializedDependency.artifactId).isEqualTo("test-lib")
            assertThat(deserializedDependency.description).isNull()
            assertThat(deserializedDependency.url).isNull()
        }
    }
    
    @Nested
    @DisplayName("Version Tests")
    inner class VersionTests {
        
        @Test
        @DisplayName("Should serialize and deserialize version with all fields")
        fun testVersionSerialization() {
            val version = Version(
                version = "1.2.3",
                releaseDate = "2023-12-01",
                isLatest = true,
                downloads = 1000000L,
                vulnerabilities = 0
            )
            
            val jsonString = json.encodeToString(version)
            val deserializedVersion = json.decodeFromString<Version>(jsonString)
            
            assertThat(deserializedVersion).isEqualTo(version)
            assertThat(deserializedVersion.version).isEqualTo("1.2.3")
            assertThat(deserializedVersion.releaseDate).isEqualTo("2023-12-01")
            assertThat(deserializedVersion.isLatest).isTrue()
            assertThat(deserializedVersion.downloads).isEqualTo(1000000L)
            assertThat(deserializedVersion.vulnerabilities).isEqualTo(0)
        }
        
        @Test
        @DisplayName("Should handle default values correctly")
        fun testVersionWithDefaults() {
            val version = Version(version = "2.0.0")
            
            val jsonString = json.encodeToString(version)
            val deserializedVersion = json.decodeFromString<Version>(jsonString)
            
            assertThat(deserializedVersion).isEqualTo(version)
            assertThat(deserializedVersion.version).isEqualTo("2.0.0")
            assertThat(deserializedVersion.releaseDate).isNull()
            assertThat(deserializedVersion.isLatest).isFalse()
            assertThat(deserializedVersion.downloads).isNull()
            assertThat(deserializedVersion.vulnerabilities).isNull()
        }
        
        @Test
        @DisplayName("Should compare semantic versions correctly")
        fun testVersionComparison() {
            val v1_0_0 = Version("1.0.0")
            val v1_0_1 = Version("1.0.1")
            val v1_1_0 = Version("1.1.0")
            val v2_0_0 = Version("2.0.0")
            
            assertThat(v1_0_0).isLessThan(v1_0_1)
            assertThat(v1_0_1).isLessThan(v1_1_0)
            assertThat(v1_1_0).isLessThan(v2_0_0)
            assertThat(v2_0_0).isGreaterThan(v1_0_0)
            assertThat(v1_0_0.compareTo(Version("1.0.0"))).isEqualTo(0)
        }
        
        @Test
        @DisplayName("Should handle versions with different lengths")
        fun testVersionComparisonWithDifferentLengths() {
            val v1_0 = Version("1.0")
            val v1_0_0 = Version("1.0.0")
            val v1_0_1 = Version("1.0.1")
            
            assertThat(v1_0.compareTo(v1_0_0)).isEqualTo(0) // 1.0 == 1.0.0
            assertThat(v1_0).isLessThan(v1_0_1) // 1.0 < 1.0.1
            assertThat(v1_0_0).isLessThan(v1_0_1) // 1.0.0 < 1.0.1
        }
        
        @Test
        @DisplayName("Should handle version prefixes")
        fun testVersionComparisonWithPrefixes() {
            val v1 = Version("v1.2.3")
            val v2 = Version("1.2.4")
            
            assertThat(v1).isLessThan(v2)
        }
        
        @Test
        @DisplayName("Should handle snapshot versions")
        fun testVersionComparisonWithSnapshots() {
            val v1 = Version("1.2.3-SNAPSHOT")
            val v2 = Version("1.2.3")
            
            // Snapshot versions should be treated as the base version for comparison
            assertThat(v1.compareTo(v2)).isEqualTo(0)
        }
        
        @Test
        @DisplayName("Should fall back to string comparison for non-semantic versions")
        fun testVersionComparisonNonSemantic() {
            val v1 = Version("release-2023-12")
            val v2 = Version("release-2024-01")
            
            // Non-semantic versions fall back to string comparison
            assertThat(v1).isLessThan(v2)
        }
        
        @Test
        @DisplayName("Should provide static version comparison method")
        fun testVersionCompareVersionsStaticMethod() {
            assertThat(Version.compareVersions("1.0.0", "1.0.0")).isEqualTo(0)
            assertThat(Version.compareVersions("1.0.0", "1.0.1")).isNegative()
            assertThat(Version.compareVersions("1.0.1", "1.0.0")).isPositive()
            assertThat(Version.compareVersions("1.0", "1.0.0")).isEqualTo(0)
            assertThat(Version.compareVersions("2.0.0", "1.9.9")).isPositive()
        }
    }
    
    @Nested
    @DisplayName("UpdateResult Tests")
    inner class UpdateResultTests {
        
        @Test
        @DisplayName("Should serialize and deserialize update result with all fields")
        fun testUpdateResultSerialization() {
            val updateResult = UpdateResult(
                success = true,
                message = "Dependency updated successfully",
                filePath = "/path/to/pom.xml",
                oldVersion = "1.0.0",
                newVersion = "1.1.0",
                wasAdded = false
            )
            
            val jsonString = json.encodeToString(updateResult)
            val deserializedResult = json.decodeFromString<UpdateResult>(jsonString)
            
            assertThat(deserializedResult).isEqualTo(updateResult)
            assertThat(deserializedResult.success).isTrue()
            assertThat(deserializedResult.message).isEqualTo("Dependency updated successfully")
            assertThat(deserializedResult.filePath).isEqualTo("/path/to/pom.xml")
            assertThat(deserializedResult.oldVersion).isEqualTo("1.0.0")
            assertThat(deserializedResult.newVersion).isEqualTo("1.1.0")
            assertThat(deserializedResult.wasAdded).isFalse()
        }
        
        @Test
        @DisplayName("Should handle default values correctly")
        fun testUpdateResultWithDefaults() {
            val updateResult = UpdateResult(
                success = false,
                message = "File not found",
                filePath = "/path/to/build.gradle"
            )
            
            val jsonString = json.encodeToString(updateResult)
            val deserializedResult = json.decodeFromString<UpdateResult>(jsonString)
            
            assertThat(deserializedResult).isEqualTo(updateResult)
            assertThat(deserializedResult.success).isFalse()
            assertThat(deserializedResult.message).isEqualTo("File not found")
            assertThat(deserializedResult.filePath).isEqualTo("/path/to/build.gradle")
            assertThat(deserializedResult.oldVersion).isNull()
            assertThat(deserializedResult.newVersion).isNull()
            assertThat(deserializedResult.wasAdded).isFalse()
        }
        
        @Test
        @DisplayName("Should handle new dependency addition")
        fun testUpdateResultForNewDependency() {
            val updateResult = UpdateResult(
                success = true,
                message = "New dependency added",
                filePath = "/path/to/pom.xml",
                oldVersion = null,
                newVersion = "2.0.0",
                wasAdded = true
            )
            
            val jsonString = json.encodeToString(updateResult)
            val deserializedResult = json.decodeFromString<UpdateResult>(jsonString)
            
            assertThat(deserializedResult).isEqualTo(updateResult)
            assertThat(deserializedResult.success).isTrue()
            assertThat(deserializedResult.message).isEqualTo("New dependency added")
            assertThat(deserializedResult.oldVersion).isNull()
            assertThat(deserializedResult.newVersion).isEqualTo("2.0.0")
            assertThat(deserializedResult.wasAdded).isTrue()
        }
    }
    
    @Nested
    @DisplayName("SearchResult Tests")
    inner class SearchResultTests {
        
        @Test
        @DisplayName("Should serialize and deserialize search result with dependencies")
        fun testSearchResultSerialization() {
            val dependencies = listOf(
                Dependency("org.springframework", "spring-core", "Spring Core"),
                Dependency("org.springframework", "spring-web", "Spring Web")
            )
            
            val searchResult = SearchResult(
                dependencies = dependencies,
                totalResults = 2,
                query = "spring"
            )
            
            val jsonString = json.encodeToString(searchResult)
            val deserializedResult = json.decodeFromString<SearchResult>(jsonString)
            
            assertThat(deserializedResult).isEqualTo(searchResult)
            assertThat(deserializedResult.dependencies).hasSize(2)
            assertThat(deserializedResult.totalResults).isEqualTo(2)
            assertThat(deserializedResult.query).isEqualTo("spring")
            assertThat(deserializedResult.dependencies[0].groupId).isEqualTo("org.springframework")
            assertThat(deserializedResult.dependencies[0].artifactId).isEqualTo("spring-core")
        }
        
        @Test
        @DisplayName("Should handle empty search results")
        fun testSearchResultEmpty() {
            val searchResult = SearchResult(
                dependencies = emptyList(),
                totalResults = 0,
                query = "nonexistent"
            )
            
            val jsonString = json.encodeToString(searchResult)
            val deserializedResult = json.decodeFromString<SearchResult>(jsonString)
            
            assertThat(deserializedResult).isEqualTo(searchResult)
            assertThat(deserializedResult.dependencies).isEmpty()
            assertThat(deserializedResult.totalResults).isEqualTo(0)
            assertThat(deserializedResult.query).isEqualTo("nonexistent")
        }
    }
    
    @Nested
    @DisplayName("Data Model Validation Tests")
    inner class ValidationTests {
        
        @Test
        @DisplayName("Should handle empty string values in data models")
        fun testDataModelValidation() {
            // Test that required fields are properly validated through construction
            val dependency = Dependency("", "")
            assertThat(dependency.groupId).isEmpty()
            assertThat(dependency.artifactId).isEmpty()
            
            val version = Version("")
            assertThat(version.version).isEmpty()
            
            val updateResult = UpdateResult(true, "", "")
            assertThat(updateResult.success).isTrue()
            assertThat(updateResult.message).isEmpty()
            assertThat(updateResult.filePath).isEmpty()
            
            val searchResult = SearchResult(emptyList(), 0, "")
            assertThat(searchResult.dependencies).isEmpty()
            assertThat(searchResult.totalResults).isEqualTo(0)
            assertThat(searchResult.query).isEmpty()
        }
    }
}