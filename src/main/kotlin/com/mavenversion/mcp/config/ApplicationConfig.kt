package com.mavenversion.mcp.config

import mu.KotlinLogging

private val log = KotlinLogging.logger {}

/**
 * Application configuration for the Maven Version MCP Server
 */
data class ApplicationConfig(
    val serverName: String = "maven-version-mcp-server",
    val serverVersion: String = "1.0.0",
    val protocolVersion: String = "2024-11-05",
    val logLevel: String = "INFO",
    val enableExamples: Boolean = false,
    val mcpServerCommand: List<String> = listOf("npx", "@modelcontextprotocol/server-playwright"),
    val workingDirectory: String? = null,
    val baseUrl: String = "https://mvnrepository.com",
    val maxRetries: Int = 3,
    val retryDelayMs: Long = 1000,
    val rateLimitDelayMs: Long = 500,
) {
    companion object {
        /**
         * Create configuration from command line arguments
         */
        fun fromArgs(args: Array<String>): ApplicationConfig {
            val config = ApplicationConfig()

            return config.copy(
                enableExamples = args.contains("example"),
                logLevel = getArgValue(args, "--log-level") ?: config.logLevel,
                workingDirectory = getArgValue(args, "--working-dir"),
                baseUrl = getArgValue(args, "--base-url") ?: config.baseUrl,
                maxRetries = getArgValue(args, "--max-retries")?.toIntOrNull() ?: config.maxRetries,
                retryDelayMs = getArgValue(args, "--retry-delay")?.toLongOrNull() ?: config.retryDelayMs,
                rateLimitDelayMs = getArgValue(args, "--rate-limit-delay")?.toLongOrNull() ?: config.rateLimitDelayMs,
            )
        }

        private fun getArgValue(
            args: Array<String>,
            key: String,
        ): String? {
            val index = args.indexOf(key)
            return if (index >= 0 && index < args.size - 1) {
                args[index + 1]
            } else {
                null
            }
        }

        /**
         * Print usage information
         */
        fun printUsage() {
            println(
                """
                Maven Version MCP Server v1.0.0
                
                Usage: java -jar maven-version-mcp-server.jar [options]
                
                Options:
                  example                    Run Playwright example instead of MCP server
                  --log-level LEVEL         Set log level (DEBUG, INFO, WARN, ERROR)
                  --working-dir DIR         Set working directory for MCP server
                  --base-url URL            Set base URL for Maven repository (default: https://mvnrepository.com)
                  --max-retries COUNT       Set maximum retry attempts (default: 3)
                  --retry-delay MS          Set retry delay in milliseconds (default: 1000)
                  --rate-limit-delay MS     Set rate limiting delay in milliseconds (default: 500)
                  --help                    Show this help message
                
                Examples:
                  java -jar maven-version-mcp-server.jar
                  java -jar maven-version-mcp-server.jar example
                  java -jar maven-version-mcp-server.jar --log-level DEBUG --max-retries 5
                """.trimIndent(),
            )
        }
    }
}
