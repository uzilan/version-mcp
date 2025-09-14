package com.mavenversion.mcp.config

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import mu.KotlinLogging
import java.io.File

private val log = KotlinLogging.logger {}

/**
 * Application configuration for the Maven Version MCP Server
 */
@Serializable
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
    val circuitBreakerFailureThreshold: Int = 5,
    val circuitBreakerRecoveryTimeoutMs: Long = 60000,
    val requestTimeoutMs: Long = 30000,
    val enableStructuredLogging: Boolean = true,
    val logToFile: Boolean = false,
    val logFile: String = "logs/maven-version-mcp-server.log",
    val maxLogFileSize: String = "10MB",
    val maxLogFiles: Int = 5,
    val enableMetrics: Boolean = false,
    val metricsPort: Int = 8081,
    val enableHealthCheck: Boolean = true,
    val healthCheckPort: Int = 8082,
) {
    companion object {
        private val json =
            Json {
                ignoreUnknownKeys = true
                prettyPrint = true
            }

        /**
         * Create configuration with multiple sources (file, environment, command line)
         */
        fun load(args: Array<String> = emptyArray()): ApplicationConfig {
            // Start with default configuration
            var config = ApplicationConfig()

            // Load from configuration file if it exists
            config = loadFromFile(config)

            // Override with environment variables
            config = loadFromEnvironment(config)

            // Override with command line arguments
            config = loadFromArgs(config, args)

            return config
        }

        /**
         * Load configuration from file
         */
        private fun loadFromFile(config: ApplicationConfig): ApplicationConfig {
            val configFile = File("config.json")
            if (configFile.exists()) {
                try {
                    val fileContent = configFile.readText()
                    val fileConfig = json.decodeFromString<ApplicationConfig>(fileContent)
                    log.info { "Loaded configuration from ${configFile.absolutePath}" }
                    return fileConfig
                } catch (e: Exception) {
                    log.warn(e) { "Failed to load configuration file: ${configFile.absolutePath}" }
                }
            }
            return config
        }

        /**
         * Load configuration from environment variables
         */
        private fun loadFromEnvironment(config: ApplicationConfig): ApplicationConfig {
            return config.copy(
                logLevel = System.getenv("MCP_LOG_LEVEL") ?: config.logLevel,
                workingDirectory = System.getenv("MCP_WORKING_DIR") ?: config.workingDirectory,
                baseUrl = System.getenv("MCP_BASE_URL") ?: config.baseUrl,
                maxRetries = System.getenv("MCP_MAX_RETRIES")?.toIntOrNull() ?: config.maxRetries,
                retryDelayMs = System.getenv("MCP_RETRY_DELAY")?.toLongOrNull() ?: config.retryDelayMs,
                rateLimitDelayMs = System.getenv("MCP_RATE_LIMIT_DELAY")?.toLongOrNull() ?: config.rateLimitDelayMs,
                circuitBreakerFailureThreshold =
                    System.getenv("MCP_CIRCUIT_BREAKER_THRESHOLD")?.toIntOrNull()
                        ?: config.circuitBreakerFailureThreshold,
                circuitBreakerRecoveryTimeoutMs =
                    System.getenv("MCP_CIRCUIT_BREAKER_TIMEOUT")?.toLongOrNull()
                        ?: config.circuitBreakerRecoveryTimeoutMs,
                requestTimeoutMs =
                    System.getenv("MCP_REQUEST_TIMEOUT")?.toLongOrNull() ?: config.requestTimeoutMs,
                enableStructuredLogging =
                    System.getenv("MCP_STRUCTURED_LOGGING")?.toBooleanStrictOrNull()
                        ?: config.enableStructuredLogging,
                logToFile = System.getenv("MCP_LOG_TO_FILE")?.toBooleanStrictOrNull() ?: config.logToFile,
                logFile = System.getenv("MCP_LOG_FILE") ?: config.logFile,
                enableMetrics = System.getenv("MCP_ENABLE_METRICS")?.toBooleanStrictOrNull() ?: config.enableMetrics,
                metricsPort = System.getenv("MCP_METRICS_PORT")?.toIntOrNull() ?: config.metricsPort,
                enableHealthCheck = System.getenv("MCP_ENABLE_HEALTH_CHECK")?.toBooleanStrictOrNull() ?: config.enableHealthCheck,
                healthCheckPort = System.getenv("MCP_HEALTH_CHECK_PORT")?.toIntOrNull() ?: config.healthCheckPort,
            )
        }

        /**
         * Load configuration from command line arguments
         */
        private fun loadFromArgs(
            config: ApplicationConfig,
            args: Array<String>,
        ): ApplicationConfig {
            return config.copy(
                enableExamples = args.contains("example"),
                logLevel = getArgValue(args, "--log-level") ?: config.logLevel,
                workingDirectory = getArgValue(args, "--working-dir") ?: config.workingDirectory,
                baseUrl = getArgValue(args, "--base-url") ?: config.baseUrl,
                maxRetries = getArgValue(args, "--max-retries")?.toIntOrNull() ?: config.maxRetries,
                retryDelayMs = getArgValue(args, "--retry-delay")?.toLongOrNull() ?: config.retryDelayMs,
                rateLimitDelayMs = getArgValue(args, "--rate-limit-delay")?.toLongOrNull() ?: config.rateLimitDelayMs,
                circuitBreakerFailureThreshold =
                    getArgValue(args, "--circuit-breaker-threshold")?.toIntOrNull()
                        ?: config.circuitBreakerFailureThreshold,
                circuitBreakerRecoveryTimeoutMs =
                    getArgValue(args, "--circuit-breaker-timeout")?.toLongOrNull()
                        ?: config.circuitBreakerRecoveryTimeoutMs,
                requestTimeoutMs =
                    getArgValue(args, "--request-timeout")?.toLongOrNull() ?: config.requestTimeoutMs,
                enableStructuredLogging =
                    getArgValue(args, "--structured-logging")?.toBooleanStrictOrNull()
                        ?: config.enableStructuredLogging,
                logToFile = getArgValue(args, "--log-to-file")?.toBooleanStrictOrNull() ?: config.logToFile,
                logFile = getArgValue(args, "--log-file") ?: config.logFile,
                enableMetrics = getArgValue(args, "--enable-metrics")?.toBooleanStrictOrNull() ?: config.enableMetrics,
                metricsPort = getArgValue(args, "--metrics-port")?.toIntOrNull() ?: config.metricsPort,
                enableHealthCheck = getArgValue(args, "--enable-health-check")?.toBooleanStrictOrNull() ?: config.enableHealthCheck,
                healthCheckPort = getArgValue(args, "--health-check-port")?.toIntOrNull() ?: config.healthCheckPort,
            )
        }

        /**
         * Create configuration from command line arguments (legacy method)
         */
        fun fromArgs(args: Array<String>): ApplicationConfig {
            return load(args)
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
                
                Configuration Sources (in order of precedence):
                  1. Command line arguments
                  2. Environment variables
                  3. config.json file
                  4. Default values
                
                Options:
                  example                           Run Playwright example instead of MCP server
                  --log-level LEVEL                Set log level (DEBUG, INFO, WARN, ERROR)
                  --working-dir DIR                Set working directory for MCP server
                  --base-url URL                   Set base URL for Maven repository
                  --max-retries COUNT              Set maximum retry attempts
                  --retry-delay MS                 Set retry delay in milliseconds
                  --rate-limit-delay MS            Set rate limiting delay in milliseconds
                  --circuit-breaker-threshold N    Set circuit breaker failure threshold
                  --circuit-breaker-timeout MS     Set circuit breaker recovery timeout
                  --request-timeout MS             Set request timeout in milliseconds
                  --structured-logging BOOLEAN     Enable/disable structured logging
                  --log-to-file BOOLEAN            Enable/disable file logging
                  --log-file PATH                  Set log file path
                  --enable-metrics BOOLEAN         Enable/disable metrics collection
                  --metrics-port PORT              Set metrics server port
                  --enable-health-check BOOLEAN    Enable/disable health check endpoint
                  --health-check-port PORT         Set health check server port
                  --help                           Show this help message
                
                Environment Variables:
                  MCP_LOG_LEVEL, MCP_WORKING_DIR, MCP_BASE_URL, MCP_MAX_RETRIES,
                  MCP_RETRY_DELAY, MCP_RATE_LIMIT_DELAY, MCP_CIRCUIT_BREAKER_THRESHOLD,
                  MCP_CIRCUIT_BREAKER_TIMEOUT, MCP_REQUEST_TIMEOUT, MCP_STRUCTURED_LOGGING,
                  MCP_LOG_TO_FILE, MCP_LOG_FILE, MCP_ENABLE_METRICS, MCP_METRICS_PORT,
                  MCP_ENABLE_HEALTH_CHECK, MCP_HEALTH_CHECK_PORT
                
                Examples:
                  java -jar maven-version-mcp-server.jar
                  java -jar maven-version-mcp-server.jar example
                  java -jar maven-version-mcp-server.jar --log-level DEBUG --max-retries 5
                  MCP_LOG_LEVEL=DEBUG java -jar maven-version-mcp-server.jar
                """.trimIndent(),
            )
        }

        /**
         * Save current configuration to file
         */
        fun saveToFile(
            config: ApplicationConfig,
            filePath: String = "config.json",
        ) {
            try {
                val configFile = File(filePath)
                configFile.parentFile?.mkdirs()
                val jsonContent = json.encodeToString(ApplicationConfig.serializer(), config)
                configFile.writeText(jsonContent)
                log.info { "Configuration saved to ${configFile.absolutePath}" }
            } catch (e: Exception) {
                log.error(e) { "Failed to save configuration to $filePath" }
            }
        }
    }
}
