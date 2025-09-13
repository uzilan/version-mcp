package com.mavenversion.mcp.web

import com.mavenversion.mcp.models.Dependency
import com.mavenversion.mcp.models.SearchResult
import mu.KotlinLogging

private val log = KotlinLogging.logger {}

/**
 * Parser for search results from mvnrepository.com HTML content
 */
class SearchResultParser {
    
    /**
     * Parse search results from mvnrepository.com HTML content
     * 
     * @param html The HTML content from the search results page
     * @param query The original search query
     * @return SearchResult containing parsed dependencies
     */
    fun parseSearchResults(html: String, query: String): SearchResult {
        log.debug { "Parsing search results for query: $query" }
        
        try {
            val dependencies = extractDependencies(html)
            val totalResults = extractTotalResults(html)
            
            log.info { "Parsed ${dependencies.size} dependencies from search results" }
            
            return SearchResult(
                dependencies = dependencies,
                totalResults = totalResults,
                query = query
            )
        } catch (e: Exception) {
            log.error(e) { "Failed to parse search results for query: $query" }
            return SearchResult(
                dependencies = emptyList(),
                totalResults = 0,
                query = query
            )
        }
    }
    
    /**
     * Extract dependency information from search results HTML
     */
    private fun extractDependencies(html: String): List<Dependency> {
        val dependencies = mutableListOf<Dependency>()
        
        // Split HTML by <div class="im"> markers and process each section
        val imDivPattern = Regex("""<div\s+class="im"[^>]*>""")
        val sections = html.split(imDivPattern)
        
        // Skip the first section (before the first <div class="im">)
        for (i in 1 until sections.size) {
            val section = sections[i]
            
            try {
                val dependency = parseSingleDependency(section)
                if (dependency != null) {
                    dependencies.add(dependency)
                }
            } catch (e: Exception) {
                log.warn(e) { "Failed to parse individual dependency from HTML section" }
                // Continue processing other sections
            }
        }
        
        return dependencies
    }
    
    /**
     * Parse a single dependency from its HTML block
     */
    private fun parseSingleDependency(html: String): Dependency? {
        // Extract the main link which contains groupId and artifactId
        // Look for patterns like: <a href="/artifact/groupId/artifactId">Title</a>
        val linkPatterns = listOf(
            Regex("""<a\s+href="/artifact/([^"]+)"[^>]*>(.*?)</a>""", RegexOption.DOT_MATCHES_ALL),
            Regex("""href="/artifact/([^"]+)"[^>]*>([^<]+)"""),
            Regex("""<a[^>]*href="/artifact/([^"]+)"[^>]*>([^<]+)""")
        )
        
        var linkMatch: MatchResult? = null
        for (pattern in linkPatterns) {
            linkMatch = pattern.find(html)
            if (linkMatch != null) break
        }
        
        if (linkMatch == null) return null
        
        val artifactPath = linkMatch.groupValues[1]
        val linkText = cleanHtmlText(linkMatch.groupValues[2]).trim()
        
        // Parse groupId and artifactId from the artifact path
        val pathParts = artifactPath.split("/")
        if (pathParts.size < 2) return null
        
        val groupId = pathParts[0]
        val artifactId = pathParts[1]
        
        // Extract description - usually in a <p> tag or div with description class
        val description = extractDescription(html)
        
        // Build the full URL
        val url = "https://mvnrepository.com/artifact/$artifactPath"
        
        return Dependency(
            groupId = groupId,
            artifactId = artifactId,
            description = description,
            url = url
        )
    }
    
    /**
     * Extract description from the dependency HTML block
     */
    private fun extractDescription(html: String): String? {
        // Try to find description in various common patterns based on mvnrepository.com structure
        val descriptionPatterns = listOf(
            // Description in im-description div
            Regex("""<div[^>]*class="[^"]*im-description[^"]*"[^>]*>(.*?)</div>""", RegexOption.DOT_MATCHES_ALL),
            // Description in <p> tag within the result
            Regex("""<p[^>]*>(.*?)</p>""", RegexOption.DOT_MATCHES_ALL),
            // Description after the title/link
            Regex("""</a>\s*</h[^>]*>\s*</div>\s*<div[^>]*>\s*<p[^>]*>(.*?)</p>""", RegexOption.DOT_MATCHES_ALL),
            // Generic description patterns
            Regex("""<(?:span|div)[^>]*class="[^"]*(?:desc|description)[^"]*"[^>]*>(.*?)</(?:span|div)>""", RegexOption.DOT_MATCHES_ALL),
            // Text content after the main link (fallback)
            Regex("""</a>.*?<p[^>]*>(.*?)</p>""", RegexOption.DOT_MATCHES_ALL)
        )
        
        for (pattern in descriptionPatterns) {
            val match = pattern.find(html)
            if (match != null) {
                val description = cleanHtmlText(match.groupValues[1])
                if (description.isNotBlank() && description.length > 10) {
                    return description
                }
            }
        }
        
        return null
    }
    
    /**
     * Extract total number of results from the search page
     */
    private fun extractTotalResults(html: String): Int {
        // Look for patterns like "Showing 1 to 20 of 1,234 results"
        val totalResultsPatterns = listOf(
            Regex("""of\s+([\d,]+)\s+results?""", RegexOption.IGNORE_CASE),
            Regex("""([\d,]+)\s+results?\s+found""", RegexOption.IGNORE_CASE),
            Regex("""Found\s+([\d,]+)\s+results?""", RegexOption.IGNORE_CASE)
        )
        
        for (pattern in totalResultsPatterns) {
            val match = pattern.find(html)
            if (match != null) {
                val numberStr = match.groupValues[1].replace(",", "")
                return numberStr.toIntOrNull() ?: 0
            }
        }
        
        // If we can't find total results, count the actual results on the page
        val resultPattern = Regex("""<div class="im"""")
        return resultPattern.findAll(html).count()
    }
    
    /**
     * Clean HTML text by removing tags and decoding entities
     */
    private fun cleanHtmlText(text: String): String {
        return text
            .replace(Regex("""<[^>]+>"""), "") // Remove HTML tags
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace(Regex("""\s+"""), " ") // Normalize whitespace
            .trim()
    }
}
