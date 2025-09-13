package com.mavenversion.mcp.web

import com.mavenversion.mcp.models.Version
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@DisplayName("VersionParser Tests")
class VersionParserTest {

    private val versionParser = VersionParser()

    @Nested
    @DisplayName("Latest Version Parsing")
    inner class LatestVersionParsingTests {

        @Test
        @DisplayName("Should parse latest version from table structure")
        fun shouldParseLatestVersionFromTable() {
            val html = """
                <html>
                <body>
                    <table class="grid">
                        <tbody>
                            <tr>
                                <td><a href="/artifact/org.springframework.boot/spring-boot-starter/3.2.1">3.2.1</a></td>
                                <td>Dec 21, 2023</td>
                                <td>1.2M</td>
                            </tr>
                            <tr>
                                <td><a href="/artifact/org.springframework.boot/spring-boot-starter/3.2.0">3.2.0</a></td>
                                <td>Nov 23, 2023</td>
                                <td>800K</td>
                            </tr>
                        </tbody>
                    </table>
                </body>
                </html>
            """.trimIndent()

            val result = versionParser.parseLatestVersion(html, "org.springframework.boot", "spring-boot-starter")

            assertThat(result).isNotNull
            assertThat(result!!.version).isEqualTo("3.2.1")
            assertThat(result.isLatest).isTrue()
            assertThat(result.releaseDate).isEqualTo("2023-12-21")
            assertThat(result.downloads).isEqualTo(1200000L)
        }

        @Test
        @DisplayName("Should parse latest version from version buttons")
        fun shouldParseLatestVersionFromButtons() {
            val html = """
                <html>
                <body>
                    <div class="vbtn">2.7.18</div>
                    <div class="vbtn">2.7.17</div>
                    <div class="vbtn">2.7.16</div>
                </body>
                </html>
            """.trimIndent()

            val result = versionParser.parseLatestVersion(html, "org.springframework", "spring-core")

            assertThat(result).isNotNull
            assertThat(result!!.version).isEqualTo("2.7.18")
            assertThat(result.isLatest).isTrue()
        }

        @Test
        @DisplayName("Should return null when no version found")
        fun shouldReturnNullWhenNoVersionFound() {
            val html = """
                <html>
                <body>
                    <p>No versions available</p>
                </body>
                </html>
            """.trimIndent()

            val result = versionParser.parseLatestVersion(html, "com.example", "nonexistent")

            assertThat(result).isNull()
        }
    }

    @Nested
    @DisplayName("All Versions Parsing")
    inner class AllVersionsParsingTests {

        @Test
        @DisplayName("Should parse all versions from table and sort correctly")
        fun shouldParseAllVersionsFromTable() {
            val html = """
                <html>
                <body>
                    <table class="grid">
                        <tbody>
                            <tr>
                                <td><a href="/artifact/org.junit.jupiter/junit-jupiter/5.10.1">5.10.1</a></td>
                                <td>2023-10-12</td>
                                <td>2.1M</td>
                            </tr>
                            <tr>
                                <td><a href="/artifact/org.junit.jupiter/junit-jupiter/5.10.0">5.10.0</a></td>
                                <td>2023-07-23</td>
                                <td>1.8M</td>
                            </tr>
                            <tr>
                                <td><a href="/artifact/org.junit.jupiter/junit-jupiter/5.9.3">5.9.3</a></td>
                                <td>2023-04-18</td>
                                <td>1.5M</td>
                            </tr>
                        </tbody>
                    </table>
                </body>
                </html>
            """.trimIndent()

            val result = versionParser.parseAllVersions(html, "org.junit.jupiter", "junit-jupiter")

            assertThat(result).hasSize(3)
            assertThat(result[0].version).isEqualTo("5.10.1")
            assertThat(result[0].isLatest).isTrue()
            assertThat(result[1].version).isEqualTo("5.10.0")
            assertThat(result[1].isLatest).isFalse()
            assertThat(result[2].version).isEqualTo("5.9.3")
            assertThat(result[2].isLatest).isFalse()
        }

        @Test
        @DisplayName("Should handle empty version list")
        fun shouldHandleEmptyVersionList() {
            val html = """
                <html>
                <body>
                    <table class="grid">
                        <tbody>
                        </tbody>
                    </table>
                </body>
                </html>
            """.trimIndent()

            val result = versionParser.parseAllVersions(html, "com.example", "empty")

            assertThat(result).isEmpty()
        }

        @Test
        @DisplayName("Should parse versions from fallback elements")
        fun shouldParseVersionsFromFallbackElements() {
            val html = """
                <html>
                <body>
                    <a href="/artifact/com.google.guava/guava/32.1.3-jre">32.1.3-jre</a>
                    <a href="/artifact/com.google.guava/guava/32.1.2-jre">32.1.2-jre</a>
                    <a href="/artifact/com.google.guava/guava/32.1.1-jre">32.1.1-jre</a>
                </body>
                </html>
            """.trimIndent()

            val result = versionParser.parseAllVersions(html, "com.google.guava", "guava")

            assertThat(result).hasSize(3)
            assertThat(result[0].version).isEqualTo("32.1.3-jre")
            assertThat(result[0].isLatest).isTrue()
        }
    }

    @Nested
    @DisplayName("Version Filtering")
    inner class VersionFilteringTests {

        private val testVersions = listOf(
            Version("3.2.1", isLatest = true),
            Version("3.2.0"),
            Version("3.2.0-SNAPSHOT"),
            Version("3.1.5"),
            Version("3.1.0-alpha1"),
            Version("3.1.0-beta2"),
            Version("3.1.0-RC1"),
            Version("3.0.12")
        )

        @Test
        @DisplayName("Should filter out snapshots by default")
        fun shouldFilterOutSnapshotsByDefault() {
            val result = versionParser.filterVersions(testVersions)

            assertThat(result).hasSize(4) // Only stable versions: 3.2.1, 3.2.0, 3.1.5, 3.0.12
            assertThat(result.map { it.version }).doesNotContain("3.2.0-SNAPSHOT")
        }

        @Test
        @DisplayName("Should filter out alpha versions by default")
        fun shouldFilterOutAlphaVersionsByDefault() {
            val result = versionParser.filterVersions(testVersions)

            assertThat(result).hasSize(4) // Only stable versions: 3.2.1, 3.2.0, 3.1.5, 3.0.12
            assertThat(result.map { it.version }).doesNotContain("3.1.0-alpha1")
        }

        @Test
        @DisplayName("Should filter out beta versions by default")
        fun shouldFilterOutBetaVersionsByDefault() {
            val result = versionParser.filterVersions(testVersions)

            assertThat(result).hasSize(4) // Only stable versions: 3.2.1, 3.2.0, 3.1.5, 3.0.12
            assertThat(result.map { it.version }).doesNotContain("3.1.0-beta2")
        }

        @Test
        @DisplayName("Should filter out RC versions by default")
        fun shouldFilterOutRCVersionsByDefault() {
            val result = versionParser.filterVersions(testVersions)

            assertThat(result).hasSize(4) // Only stable versions: 3.2.1, 3.2.0, 3.1.5, 3.0.12
            assertThat(result.map { it.version }).doesNotContain("3.1.0-RC1")
        }

        @Test
        @DisplayName("Should include snapshots when requested")
        fun shouldIncludeSnapshotsWhenRequested() {
            val result = versionParser.filterVersions(testVersions, includeSnapshots = true)

            assertThat(result.map { it.version }).contains("3.2.0-SNAPSHOT")
        }

        @Test
        @DisplayName("Should include alpha versions when requested")
        fun shouldIncludeAlphaVersionsWhenRequested() {
            val result = versionParser.filterVersions(testVersions, includeAlpha = true)

            assertThat(result.map { it.version }).contains("3.1.0-alpha1")
        }

        @Test
        @DisplayName("Should include beta versions when requested")
        fun shouldIncludeBetaVersionsWhenRequested() {
            val result = versionParser.filterVersions(testVersions, includeBeta = true)

            assertThat(result.map { it.version }).contains("3.1.0-beta2")
        }

        @Test
        @DisplayName("Should include RC versions when requested")
        fun shouldIncludeRCVersionsWhenRequested() {
            val result = versionParser.filterVersions(testVersions, includeRC = true)

            assertThat(result.map { it.version }).contains("3.1.0-RC1")
        }

        @Test
        @DisplayName("Should limit results when limit specified")
        fun shouldLimitResultsWhenLimitSpecified() {
            val result = versionParser.filterVersions(testVersions, limit = 3)

            assertThat(result).hasSize(3)
        }

        @Test
        @DisplayName("Should not limit when limit is null")
        fun shouldNotLimitWhenLimitIsNull() {
            val result = versionParser.filterVersions(testVersions, limit = null)

            assertThat(result).hasSize(4) // 4 after filtering out pre-release versions
        }
    }

    @Nested
    @DisplayName("Version Text Extraction")
    inner class VersionTextExtractionTests {

        @Test
        @DisplayName("Should extract version from table cell link")
        fun shouldExtractVersionFromTableCellLink() {
            val html = """
                <tr>
                    <td><a href="/artifact/group/artifact/1.2.3">1.2.3</a></td>
                    <td>2023-12-01</td>
                </tr>
            """.trimIndent()

            val result = versionParser.parseAllVersions("<table><tbody>$html</tbody></table>", "group", "artifact")

            assertThat(result).hasSize(1)
            assertThat(result[0].version).isEqualTo("1.2.3")
        }

        @Test
        @DisplayName("Should extract version from href when text is not version")
        fun shouldExtractVersionFromHrefWhenTextIsNotVersion() {
            val html = """
                <a href="/artifact/org.springframework/spring-core/5.3.23">Spring Core</a>
            """.trimIndent()

            val result = versionParser.parseAllVersions(html, "org.springframework", "spring-core")

            assertThat(result).hasSize(1)
            assertThat(result[0].version).isEqualTo("5.3.23")
        }
    }

    @Nested
    @DisplayName("Date Parsing")
    inner class DateParsingTests {

        @Test
        @DisplayName("Should parse ISO date format")
        fun shouldParseISODateFormat() {
            val html = """
                <table class="grid">
                    <tbody>
                        <tr>
                            <td><a href="/artifact/group/artifact/1.0.0">1.0.0</a></td>
                            <td>2023-12-21</td>
                        </tr>
                    </tbody>
                </table>
            """.trimIndent()

            val result = versionParser.parseAllVersions(html, "group", "artifact")

            assertThat(result).hasSize(1)
            assertThat(result[0].releaseDate).isEqualTo("2023-12-21")
        }

        @Test
        @DisplayName("Should parse US date format")
        fun shouldParseUSDateFormat() {
            val html = """
                <table class="grid">
                    <tbody>
                        <tr>
                            <td><a href="/artifact/group/artifact/1.0.0">1.0.0</a></td>
                            <td>12/21/2023</td>
                        </tr>
                    </tbody>
                </table>
            """.trimIndent()

            val result = versionParser.parseAllVersions(html, "group", "artifact")

            assertThat(result).hasSize(1)
            assertThat(result[0].releaseDate).isEqualTo("2023-12-21")
        }

        @Test
        @DisplayName("Should parse month name date format")
        fun shouldParseMonthNameDateFormat() {
            val html = """
                <table class="grid">
                    <tbody>
                        <tr>
                            <td><a href="/artifact/group/artifact/1.0.0">1.0.0</a></td>
                            <td>Dec 21, 2023</td>
                        </tr>
                    </tbody>
                </table>
            """.trimIndent()

            val result = versionParser.parseAllVersions(html, "group", "artifact")

            assertThat(result).hasSize(1)
            assertThat(result[0].releaseDate).isEqualTo("2023-12-21")
        }
    }

    @Nested
    @DisplayName("Download Count Parsing")
    inner class DownloadCountParsingTests {

        @Test
        @DisplayName("Should parse download count with K suffix")
        fun shouldParseDownloadCountWithKSuffix() {
            val html = """
                <table class="grid">
                    <tbody>
                        <tr>
                            <td><a href="/artifact/group/artifact/1.0.0">1.0.0</a></td>
                            <td>2023-12-21</td>
                            <td>1.2K</td>
                        </tr>
                    </tbody>
                </table>
            """.trimIndent()

            val result = versionParser.parseAllVersions(html, "group", "artifact")

            assertThat(result).hasSize(1)
            assertThat(result[0].downloads).isEqualTo(1200L)
        }

        @Test
        @DisplayName("Should parse download count with M suffix")
        fun shouldParseDownloadCountWithMSuffix() {
            val html = """
                <table class="grid">
                    <tbody>
                        <tr>
                            <td><a href="/artifact/group/artifact/1.0.0">1.0.0</a></td>
                            <td>2023-12-21</td>
                            <td>2.5M</td>
                        </tr>
                    </tbody>
                </table>
            """.trimIndent()

            val result = versionParser.parseAllVersions(html, "group", "artifact")

            assertThat(result).hasSize(1)
            assertThat(result[0].downloads).isEqualTo(2500000L)
        }

        @Test
        @DisplayName("Should parse raw download count")
        fun shouldParseRawDownloadCount() {
            val html = """
                <table class="grid">
                    <tbody>
                        <tr>
                            <td><a href="/artifact/group/artifact/1.0.0">1.0.0</a></td>
                            <td>2023-12-21</td>
                            <td>123,456</td>
                        </tr>
                    </tbody>
                </table>
            """.trimIndent()

            val result = versionParser.parseAllVersions(html, "group", "artifact")

            assertThat(result).hasSize(1)
            assertThat(result[0].downloads).isEqualTo(123456L)
        }
    }

    @Nested
    @DisplayName("Error Handling")
    inner class ErrorHandlingTests {

        @Test
        @DisplayName("Should handle malformed HTML gracefully")
        fun shouldHandleMalformedHTMLGracefully() {
            val html = "<html><body><div>Not a valid structure"

            val result = versionParser.parseAllVersions(html, "group", "artifact")

            assertThat(result).isEmpty()
        }

        @Test
        @DisplayName("Should handle empty HTML gracefully")
        fun shouldHandleEmptyHTMLGracefully() {
            val html = ""

            val result = versionParser.parseLatestVersion(html, "group", "artifact")

            assertThat(result).isNull()
        }

        @Test
        @DisplayName("Should handle invalid date formats gracefully")
        fun shouldHandleInvalidDateFormatsGracefully() {
            val html = """
                <table class="grid">
                    <tbody>
                        <tr>
                            <td><a href="/artifact/group/artifact/1.0.0">1.0.0</a></td>
                            <td>invalid-date</td>
                        </tr>
                    </tbody>
                </table>
            """.trimIndent()

            val result = versionParser.parseAllVersions(html, "group", "artifact")

            assertThat(result).hasSize(1)
            assertThat(result[0].version).isEqualTo("1.0.0")
            assertThat(result[0].releaseDate).isNull()
        }
    }
}