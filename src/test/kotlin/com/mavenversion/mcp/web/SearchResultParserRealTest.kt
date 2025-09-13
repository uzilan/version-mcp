package com.mavenversion.mcp.web

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("SearchResultParser Real HTML Tests")
class SearchResultParserRealTest {

    private val parser = SearchResultParser()

    @Test
    @DisplayName("Should parse realistic mvnrepository.com HTML structure")
    fun shouldParseRealisticHtml() {
        val realHtml = """
            <div class="search-results">
                <div class="search-header">
                    <h2>Search Results</h2>
                    <p>Showing 1 to 20 of 1,234 results</p>
                </div>
                
                <div class="im">
                    <div class="im-header">
                        <h2 class="im-title">
                            <a href="/artifact/org.springframework.boot/spring-boot-starter">Spring Boot Starter</a>
                        </h2>
                    </div>
                    <div class="im-description">
                        <p>Core starter, including auto-configuration support, logging and YAML</p>
                    </div>
                    <div class="im-usage">
                        <span class="usage-count">Used By: 123,456 artifacts</span>
                    </div>
                </div>
                
                <div class="im">
                    <div class="im-header">
                        <h2 class="im-title">
                            <a href="/artifact/org.springframework.boot/spring-boot-starter-web">Spring Boot Starter Web</a>
                        </h2>
                    </div>
                    <div class="im-description">
                        <p>Starter for building web, including RESTful, applications using Spring MVC</p>
                    </div>
                    <div class="im-usage">
                        <span class="usage-count">Used By: 98,765 artifacts</span>
                    </div>
                </div>
            </div>
        """.trimIndent()

        val result = parser.parseSearchResults(realHtml, "spring-boot")

        assertThat(result.query).isEqualTo("spring-boot")
        assertThat(result.totalResults).isEqualTo(1234)
        assertThat(result.dependencies).hasSize(2)

        val firstDep = result.dependencies[0]
        assertThat(firstDep.groupId).isEqualTo("org.springframework.boot")
        assertThat(firstDep.artifactId).isEqualTo("spring-boot-starter")
        assertThat(firstDep.description).isEqualTo("Core starter, including auto-configuration support, logging and YAML")
        assertThat(firstDep.url).isEqualTo("https://mvnrepository.com/artifact/org.springframework.boot/spring-boot-starter")

        val secondDep = result.dependencies[1]
        assertThat(secondDep.groupId).isEqualTo("org.springframework.boot")
        assertThat(secondDep.artifactId).isEqualTo("spring-boot-starter-web")
        assertThat(secondDep.description).isEqualTo("Starter for building web, including RESTful, applications using Spring MVC")
    }

    @Test
    @DisplayName("Should handle minimal HTML structure")
    fun shouldHandleMinimalHtml() {
        val minimalHtml = """
            <div class="im">
                <a href="/artifact/junit/junit">JUnit</a>
                <p>JUnit is a unit testing framework for Java</p>
            </div>
        """.trimIndent()

        val result = parser.parseSearchResults(minimalHtml, "junit")

        assertThat(result.dependencies).hasSize(1)
        val dep = result.dependencies[0]
        assertThat(dep.groupId).isEqualTo("junit")
        assertThat(dep.artifactId).isEqualTo("junit")
        assertThat(dep.description).isEqualTo("JUnit is a unit testing framework for Java")
    }

    @Test
    @DisplayName("Should extract result count from realistic patterns")
    fun shouldExtractResultCountFromRealisticPatterns() {
        val htmlWithCount = """
            <div class="search-header">
                <p>Showing 1 to 20 of 1,234 results</p>
            </div>
            <div class="im">
                <a href="/artifact/test/test">Test</a>
            </div>
        """.trimIndent()

        val result = parser.parseSearchResults(htmlWithCount, "test")

        assertThat(result.totalResults).isEqualTo(1234)
    }
}