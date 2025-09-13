package com.mavenversion.mcp.models

import kotlinx.serialization.Serializable

/**
 * Represents a Maven/Gradle dependency with metadata
 */
@Serializable
data class Dependency(
    val groupId: String,
    val artifactId: String,
    val description: String? = null,
    val url: String? = null,
)

/**
 * Represents a version of a dependency with comparison capabilities
 */
@Serializable
data class Version(
    val version: String,
    val releaseDate: String? = null,
    val isLatest: Boolean = false,
    val downloads: Long? = null,
    val vulnerabilities: Int? = null,
) : Comparable<Version> {
    /**
     * Compares versions using semantic versioning rules where possible
     * Falls back to string comparison for non-semantic versions
     */
    override fun compareTo(other: Version): Int {
        return compareVersions(this.version, other.version)
    }

    companion object {
        /**
         * Compares two version strings using semantic versioning rules
         * Returns negative if v1 < v2, positive if v1 > v2, zero if equal
         */
        fun compareVersions(
            v1: String,
            v2: String,
        ): Int {
            if (v1 == v2) return 0

            // Try semantic version comparison first
            val v1Parts = parseVersionParts(v1)
            val v2Parts = parseVersionParts(v2)

            if (v1Parts != null && v2Parts != null) {
                return compareSemanticVersions(v1Parts, v2Parts)
            }

            // Fall back to string comparison for non-semantic versions
            return v1.compareTo(v2)
        }

        private fun parseVersionParts(version: String): List<Int>? {
            return try {
                // Remove common prefixes and suffixes
                val cleanVersion =
                    version.removePrefix("v")
                        .split("-")[0] // Remove snapshot/beta suffixes
                        .split("+")[0] // Remove build metadata

                cleanVersion.split(".")
                    .map { it.toInt() }
            } catch (e: NumberFormatException) {
                null
            }
        }

        private fun compareSemanticVersions(
            v1Parts: List<Int>,
            v2Parts: List<Int>,
        ): Int {
            val maxLength = maxOf(v1Parts.size, v2Parts.size)

            for (i in 0 until maxLength) {
                val v1Part = v1Parts.getOrElse(i) { 0 }
                val v2Part = v2Parts.getOrElse(i) { 0 }

                val comparison = v1Part.compareTo(v2Part)
                if (comparison != 0) return comparison
            }

            return 0
        }
    }
}

/**
 * Represents the result of updating a dependency in a project file
 */
@Serializable
data class UpdateResult(
    val success: Boolean,
    val message: String,
    val filePath: String,
    val oldVersion: String? = null,
    val newVersion: String? = null,
    val wasAdded: Boolean = false,
)

/**
 * Represents search results from dependency search operations
 */
@Serializable
data class SearchResult(
    val dependencies: List<Dependency>,
    val totalResults: Int,
    val query: String,
)
