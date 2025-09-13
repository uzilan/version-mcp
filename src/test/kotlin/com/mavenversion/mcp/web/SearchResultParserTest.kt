package com.mavenversion.mcp.web

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@DisplayName("SearchResultParser Tests")
class SearchResultParserTest {

    private val parser = SearchResultParser()

    @Nested
    @DisplayName("Parse Search Results")
    inner class ParseSearchResultsTests {

        @Test
        @DisplayName("Should parse valid search results with multiple dependencies")
        fun shouldParseValidSearchResults() {
            val html = """
                <html>
                <body>
                    <div class="im">
                        <a href="/artifact/org.springframework/spring-core">Spring Core</a>
                        <p>Core utilities used by other modules to define packaging and version strategies.</p>
                    </div>
                    <div class="im">
                        <a href="/artifact/org.apache.commons/commons-lang3">Apache Commons Lang</a>
                        <p>Apache Commons Lang, a package of Java utility classes.</p>
                    </div>
                    <div>Showing 1 to 2 of 1,234 results</div>
                </body>
                </html>
            """.trimIndent()

            val result = parser.parseSearchResults(html, "spring")

            assertThat(result.query).isEqualTo("spring")
            assertThat(result.totalResults).isEqualTo(1234)
            assertThat(result.dependencies).hasSize(2)

            val firstDep = result.dependencies[0]
            assertThat(firstDep.groupId).isEqualTo("org.springframework")
            assertThat(firstDep.artifactId).isEqualTo("spring-core")
            assertThat(firstDep.description).isEqualTo("Core utilities used by other modules to define packaging and version strategies.")
            assertThat(firstDep.url).isEqualTo("https://mvnrepository.com/artifact/org.springframework/spring-core")

            val secondDep = result.dependencies[1]
            assertThat(secondDep.groupId).isEqualTo("org.apache.commons")
            assertThat(secondDep.artifactId).isEqualTo("commons-lang3")
            assertThat(secondDep.description).isEqualTo("Apache Commons Lang, a package of Java utility classes.")
        }

        @Test
        @DisplayName("Should handle empty search results")
        fun shouldHandleEmptySearchResults() {
            val html = """
                <html>
                <body>
                    <div>No results found for your search.</div>
                </body>
                </html>
            """.trimIndent()

            val result = parser.parseSearchResults(html, "nonexistent")

            assertThat(result.query).isEqualTo("nonexistent")
            assertThat(result.totalResults).isEqualTo(0)
            assertThat(result.dependencies).isEmpty()
        }

        @Test
        @DisplayName("Should handle malformed HTML gracefully")
        fun shouldHandleMalformedHtml() {
            val html = """
                <html>
                <body>
                    <div class="im">
                        <a href="/artifact/incomplete
                    </div>
                    <div class="im">
                        <a href="/artifact/org.example/valid-artifact">Valid Artifact</a>
                    </div>
                </body>
                </html>
            """.trimIndent()

            val result = parser.parseSearchResults(html, "test")

            assertThat(result.query).isEqualTo("test")
            assertThat(result.dependencies).hasSize(1)
            assertThat(result.dependencies[0].groupId).isEqualTo("org.example")
            assertThat(result.dependencies[0].artifactId).isEqualTo("valid-artifact")
        }

        @Test
        @DisplayName("Should parse dependencies without descriptions")
        fun shouldParseDependenciesWithoutDescriptions() {
            val html = """
                <html>
                <body>
                    <div class="im">
                        <a href="/artifact/com.example/test-artifact">Test Artifact</a>
                    </div>
                </body>
                </html>
            """.trimIndent()

            val result = parser.parseSearchResults(html, "test")

            assertThat(result.dependencies).hasSize(1)
            val dep = result.dependencies[0]
            assertThat(dep.groupId).isEqualTo("com.example")
            assertThat(dep.artifactId).isEqualTo("test-artifact")
            assertThat(dep.description).isNull()
            assertThat(dep.url).isEqualTo("https://mvnrepository.com/artifact/com.example/test-artifact")
        }

        @Test
        @DisplayName("Should handle complex artifact paths")
        fun shouldHandleComplexArtifactPaths() {
            val html = """
                <html>
                <body>
                    <div class="im">
                        <a href="/artifact/org.springframework.boot/spring-boot-starter-web/2.7.0">Spring Boot Web Starter</a>
                        <p>Starter for building web applications using Spring MVC.</p>
                    </div>
                </body>
                </html>
            """.trimIndent()

            val result = parser.parseSearchResults(html, "spring boot")

            assertThat(result.dependencies).hasSize(1)
            val dep = result.dependencies[0]
            assertThat(dep.groupId).isEqualTo("org.springframework.boot")
            assertThat(dep.artifactId).isEqualTo("spring-boot-starter-web")
            assertThat(dep.description).isEqualTo("Starter for building web applications using Spring MVC.")
        }

        @Test
        @DisplayName("Should extract total results from various formats")
        fun shouldExtractTotalResultsFromVariousFormats() {
            val testCases = listOf(
                "Showing 1 to 20 of 1,234 results" to 1234,
                "Found 567 results" to 567,
                "89 results found" to 89,
                "of 12,345 results" to 12345
            )

            testCases.forEach { (htmlText, expectedTotal) ->
                val html = """
                    <html>
                    <body>
                        <div>$htmlText</div>
                        <div class="im">
                            <a href="/artifact/com.example/test">Test</a>
                        </div>
                    </body>
                    </html>
                """.trimIndent()

                val result = parser.parseSearchResults(html, "test")
                assertThat(result.totalResults).isEqualTo(expectedTotal)
            }
        }

        @Test
        @DisplayName("Should clean HTML entities in descriptions")
        fun shouldCleanHtmlEntitiesInDescriptions() {
            val html = """
                <html>
                <body>
                    <div class="im">
                        <a href="/artifact/com.example/test">Test Artifact</a>
                        <p>A &amp; B &lt;utility&gt; for &quot;testing&quot; &#39;purposes&#39;</p>
                    </div>
                </body>
                </html>
            """.trimIndent()

            val result = parser.parseSearchResults(html, "test")

            assertThat(result.dependencies).hasSize(1)
            val dep = result.dependencies[0]
            assertThat(dep.description).isEqualTo("A & B <utility> for \"testing\" 'purposes'")
        }

        @Test
        @DisplayName("Should handle completely invalid HTML")
        fun shouldHandleCompletelyInvalidHtml() {
            val html = "This is not HTML at all!"

            val result = parser.parseSearchResults(html, "test")

            assertThat(result.query).isEqualTo("test")
            assertThat(result.totalResults).isEqualTo(0)
            assertThat(result.dependencies).isEmpty()
        }

        @Test
        @DisplayName("Should handle null or empty HTML")
        fun shouldHandleNullOrEmptyHtml() {
            val emptyResult = parser.parseSearchResults("", "test")
            assertThat(emptyResult.dependencies).isEmpty()
            assertThat(emptyResult.totalResults).isEqualTo(0)
            assertThat(emptyResult.query).isEqualTo("test")
        }
    }

    @Nested
    @DisplayName("Edge Cases")
    inner class EdgeCaseTests {

        @Test
        @DisplayName("Should handle artifacts with single-part paths")
        fun shouldHandleArtifactsWithSinglePartPaths() {
            val html = """
                <html>
                <body>
                    <div class="im">
                        <a href="/artifact/invalid-path">Invalid Path</a>
                    </div>
                    <div class="im">
                        <a href="/artifact/valid/artifact">Valid Artifact</a>
                    </div>
                </body>
                </html>
            """.trimIndent()

            val result = parser.parseSearchResults(html, "test")

            // Should only parse the valid artifact, skip the invalid one
            assertThat(result.dependencies).hasSize(1)
            assertThat(result.dependencies[0].groupId).isEqualTo("valid")
            assertThat(result.dependencies[0].artifactId).isEqualTo("artifact")
        }

        @Test
        @DisplayName("Should handle very long descriptions")
        fun shouldHandleVeryLongDescriptions() {
            val longDescription = "A".repeat(1000)
            val html = """
                <html>
                <body>
                    <div class="im">
                        <a href="/artifact/com.example/test">Test</a>
                        <p>$longDescription</p>
                    </div>
                </body>
                </html>
            """.trimIndent()

            val result = parser.parseSearchResults(html, "test")

            assertThat(result.dependencies).hasSize(1)
            assertThat(result.dependencies[0].description).isEqualTo(longDescription)
        }

        @Test
        @DisplayName("Should handle mixed valid and invalid results")
        fun shouldHandleMixedValidAndInvalidResults() {
            val html = """
                <html>
                <body>
                    <div class="im">
                        <!-- This one is missing the href -->
                        <a>Invalid Link</a>
                    </div>
                    <div class="im">
                        <a href="/artifact/com.valid/artifact">Valid Artifact</a>
                        <p>Valid description</p>
                    </div>
                    <div class="im">
                        <!-- This one has malformed href -->
                        <a href="not-an-artifact-link">Another Invalid</a>
                    </div>
                </body>
                </html>
            """.trimIndent()

            val result = parser.parseSearchResults(html, "test")

            assertThat(result.dependencies).hasSize(1)
            assertThat(result.dependencies[0].groupId).isEqualTo("com.valid")
            assertThat(result.dependencies[0].artifactId).isEqualTo("artifact")
            assertThat(result.dependencies[0].description).isEqualTo("Valid description")
        }
    }
}