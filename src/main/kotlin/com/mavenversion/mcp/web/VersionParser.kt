package com.mavenversion.mcp.web

import com.mavenversion.mcp.models.Version
import mu.KotlinLogging
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

private val log = KotlinLogging.logger {}

/**
 * Parser for version information from dependency pages on mvnrepository.com
 */
class VersionParser {

    /**
     * Parse the latest version from a dependency page HTML
     */
    fun parseLatestVersion(html: String, groupId: String, artifactId: String): Version? {
        return try {
            log.debug { "Parsing latest version for $groupId:$artifactId" }
            val doc = Jsoup.parse(html)

            // Look for the latest version in the version table or version list
            val latestVersionElement = findLatestVersionElement(doc)

            if (latestVersionElement != null) {
                parseVersionFromElement(latestVersionElement, isLatest = true)
            } else {
                log.warn { "Could not find latest version for $groupId:$artifactId" }
                null
            }
        } catch (e: Exception) {
            log.error(e) { "Failed to parse latest version for $groupId:$artifactId" }
            null
        }
    }

    /**
     * Parse all versions from a dependency page HTML
     */
    fun parseAllVersions(html: String, groupId: String, artifactId: String): List<Version> {
        return try {
            log.debug { "Parsing all versions for $groupId:$artifactId" }
            val doc = Jsoup.parse(html)

            val versions = mutableListOf<Version>()

            // Look for version table rows
            val versionRows = doc.select("table.grid tbody tr")
            if (versionRows.isNotEmpty()) {
                log.debug { "Found ${versionRows.size} version rows in table" }
                versionRows.forEachIndexed { index, row ->
                    parseVersionFromElement(row, isLatest = index == 0)?.let { version ->
                        versions.add(version)
                    }
                }
            } else {
                // Fallback: look for version links or divs
                val versionElements = doc.select(".vbtn, .version-item, a[href*='/artifact/']")
                log.debug { "Found ${versionElements.size} version elements as fallback" }
                versionElements.forEachIndexed { index, element ->
                    parseVersionFromElement(element, isLatest = index == 0)?.let { version ->
                        versions.add(version)
                    }
                }
            }

            // Sort versions in descending order (newest first)
            val sortedVersions = versions.sortedDescending()

            // Mark the first version as latest if we have versions
            if (sortedVersions.isNotEmpty()) {
                val latestVersion = sortedVersions.first().copy(isLatest = true)
                val otherVersions = sortedVersions.drop(1).map { it.copy(isLatest = false) }
                listOf(latestVersion) + otherVersions
            } else {
                emptyList()
            }
        } catch (e: Exception) {
            log.error(e) { "Failed to parse all versions for $groupId:$artifactId" }
            emptyList()
        }
    }

    /**
     * Filter versions based on criteria
     */
    fun filterVersions(
        versions: List<Version>,
        includeSnapshots: Boolean = false,
        includeAlpha: Boolean = false,
        includeBeta: Boolean = false,
        includeRC: Boolean = false,
        limit: Int? = null
    ): List<Version> {

        var filtered = versions

        if (!includeSnapshots) {
            filtered = filtered.filter { !it.version.contains("SNAPSHOT", ignoreCase = true) }
        }

        if (!includeAlpha) {
            filtered = filtered.filter {
                !it.version.contains("alpha", ignoreCase = true) &&
                        !it.version.contains("-a", ignoreCase = true)
            }
        }

        if (!includeBeta) {
            filtered = filtered.filter {
                !it.version.contains("beta", ignoreCase = true) &&
                        !it.version.contains("-b", ignoreCase = true)
            }
        }

        if (!includeRC) {
            filtered = filtered.filter {
                !it.version.contains("RC", ignoreCase = true) &&
                        !it.version.contains("rc", ignoreCase = true)
            }
        }

        return if (limit != null && limit > 0) {
            filtered.take(limit)
        } else {
            filtered
        }
    }

    /**
     * Find the latest version element in the document
     */
    private fun findLatestVersionElement(doc: Document): Element? {
        // Try different selectors for finding the latest version
        val selectors = listOf(
            "table.grid tbody tr:first-child", // First row in version table
            ".vbtn:first-child", // First version button
            ".version-item:first-child", // First version item
            "a[href*='/artifact/']:first-child" // First artifact link
        )

        for (selector in selectors) {
            val element = doc.selectFirst(selector)
            if (element != null) {
                log.debug { "Found latest version element using selector: $selector" }
                return element
            }
        }

        return null
    }

    /**
     * Parse version information from a DOM element
     */
    private fun parseVersionFromElement(element: Element, isLatest: Boolean = false): Version? {
        return try {
            val versionText = extractVersionText(element)
            if (versionText.isBlank()) {
                return null
            }

            val releaseDate = extractReleaseDate(element)
            val downloads = extractDownloads(element)
            val vulnerabilities = extractVulnerabilities(element)

            Version(
                version = versionText,
                releaseDate = releaseDate,
                isLatest = isLatest,
                downloads = downloads,
                vulnerabilities = vulnerabilities
            )
        } catch (e: Exception) {
            log.warn(e) { "Failed to parse version from element: ${element.text()}" }
            null
        }
    }

    /**
     * Extract version text from an element
     */
    private fun extractVersionText(element: Element): String {
        // Try different approaches to extract version text

        // If it's a table row, look for version in first cell
        val versionCell = element.selectFirst("td:first-child")
        if (versionCell != null) {
            val versionLink = versionCell.selectFirst("a")
            if (versionLink != null) {
                return versionLink.text().trim()
            }
            return versionCell.text().trim()
        }

        // If it's a link, extract from href or text
        if (element.tagName() == "a") {
            val href = element.attr("href")
            // Extract version from URL like /artifact/group/artifact/1.2.3
            val versionFromHref = href.substringAfterLast("/")
            if (versionFromHref.isNotBlank() && versionFromHref.matches(Regex("\\d+.*"))) {
                return versionFromHref
            }
            return element.text().trim()
        }

        // If it's a version button or item, get text content
        if (element.hasClass("vbtn") || element.hasClass("version-item")) {
            return element.text().trim()
        }

        // Fallback: look for any text that looks like a version
        val text = element.text().trim()
        val versionPattern = Regex("(\\d+(?:\\.\\d+)*(?:[.-]\\w+)*)")
        val match = versionPattern.find(text)
        return match?.value ?: text
    }

    /**
     * Extract release date from an element
     */
    private fun extractReleaseDate(element: Element): String? {
        // Look for date in various formats
        val dateSelectors = listOf(
            "td:nth-child(2)", // Second column in table
            ".date",
            ".release-date",
            "time"
        )

        for (selector in dateSelectors) {
            val dateElement = element.selectFirst(selector)
            if (dateElement != null) {
                val dateText = dateElement.text().trim()
                val normalizedDate = normalizeDateString(dateText)
                if (normalizedDate != null) {
                    return normalizedDate
                }
            }
        }

        // Look for date patterns in the element text
        val text = element.text()
        val datePatterns = listOf(
            Regex("(\\d{4}-\\d{2}-\\d{2})"), // YYYY-MM-DD
            Regex("(\\d{2}/\\d{2}/\\d{4})"), // MM/DD/YYYY
            Regex("(\\d{2}-\\d{2}-\\d{4})"), // MM-DD-YYYY
            Regex("(\\w{3} \\d{1,2}, \\d{4})") // Jan 1, 2023
        )

        for (pattern in datePatterns) {
            val match = pattern.find(text)
            if (match != null) {
                val normalizedDate = normalizeDateString(match.value)
                if (normalizedDate != null) {
                    return normalizedDate
                }
            }
        }

        return null
    }

    /**
     * Extract download count from an element
     */
    private fun extractDownloads(element: Element): Long? {
        val downloadSelectors = listOf(
            "td:nth-child(3)", // Third column in table
            ".downloads",
            ".usage-count"
        )

        for (selector in downloadSelectors) {
            val downloadElement = element.selectFirst(selector)
            if (downloadElement != null) {
                val downloadText = downloadElement.text().trim()
                val downloads = parseDownloadCount(downloadText)
                if (downloads != null) {
                    return downloads
                }
            }
        }

        // Look for download patterns in the element text
        val text = element.text()
        val downloadPattern =
            Regex("(\\d+(?:,\\d+)*(?:\\.\\d+)?[KMB]?)\\s*(?:downloads?|used by)", RegexOption.IGNORE_CASE)
        val match = downloadPattern.find(text)
        if (match != null) {
            return parseDownloadCount(match.groupValues[1])
        }

        return null
    }

    /**
     * Extract vulnerability count from an element
     */
    private fun extractVulnerabilities(element: Element): Int? {
        val vulnSelectors = listOf(
            ".vulnerabilities",
            ".security",
            ".vuln-count"
        )

        for (selector in vulnSelectors) {
            val vulnElement = element.selectFirst(selector)
            if (vulnElement != null) {
                val vulnText = vulnElement.text().trim()
                val vulnCount = parseVulnerabilityCount(vulnText)
                if (vulnCount != null) {
                    return vulnCount
                }
            }
        }

        return null
    }

    /**
     * Normalize date string to ISO format
     */
    private fun normalizeDateString(dateStr: String): String? {
        val formatters = listOf(
            DateTimeFormatter.ofPattern("yyyy-MM-dd"),
            DateTimeFormatter.ofPattern("MM/dd/yyyy"),
            DateTimeFormatter.ofPattern("MM-dd-yyyy"),
            DateTimeFormatter.ofPattern("MMM d, yyyy"),
            DateTimeFormatter.ofPattern("MMM dd, yyyy")
        )

        for (formatter in formatters) {
            try {
                val date = LocalDate.parse(dateStr, formatter)
                return date.format(DateTimeFormatter.ISO_LOCAL_DATE)
            } catch (e: DateTimeParseException) {
                // Try next formatter
            }
        }

        return null
    }

    /**
     * Parse download count string to number
     */
    private fun parseDownloadCount(downloadStr: String): Long? {
        return try {
            val cleanStr = downloadStr.replace(",", "").trim()
            when {
                cleanStr.endsWith("K", ignoreCase = true) -> {
                    val number = cleanStr.dropLast(1).toDouble()
                    (number * 1000).toLong()
                }

                cleanStr.endsWith("M", ignoreCase = true) -> {
                    val number = cleanStr.dropLast(1).toDouble()
                    (number * 1000000).toLong()
                }

                cleanStr.endsWith("B", ignoreCase = true) -> {
                    val number = cleanStr.dropLast(1).toDouble()
                    (number * 1000000000).toLong()
                }

                else -> cleanStr.toLongOrNull()
            }
        } catch (e: NumberFormatException) {
            null
        }
    }

    /**
     * Parse vulnerability count string to number
     */
    private fun parseVulnerabilityCount(vulnStr: String): Int? {
        return try {
            val numberPattern = Regex("(\\d+)")
            val match = numberPattern.find(vulnStr)
            match?.value?.toIntOrNull()
        } catch (e: NumberFormatException) {
            null
        }
    }
}
