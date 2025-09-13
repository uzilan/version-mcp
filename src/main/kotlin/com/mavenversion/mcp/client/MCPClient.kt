package com.mavenversion.mcp.client

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import mu.KotlinLogging

private val log = KotlinLogging.logger {}

/**
 * Client for communicating with MCP servers
 */
class MCPClient(
    private val serverUrl: String = "http://localhost:3000",
) {
    private val httpClient =
        HttpClient(CIO) {
            install(ContentNegotiation) {
                json(
                    Json {
                        ignoreUnknownKeys = true
                        isLenient = true
                    },
                )
            }
        }

    private val connectionMutex = Mutex()
    private var isConnected = false

    /**
     * Connect to the MCP server
     */
    suspend fun connect(): Result<Unit> =
        runCatching {
            connectionMutex.withLock {
                if (isConnected) {
                    log.debug { "Already connected to MCP server at $serverUrl" }
                    return@withLock
                }

                log.info { "Connecting to MCP server at $serverUrl" }

                // Test connection with a simple ping
                val response = httpClient.get("$serverUrl/health")

                if (response.status.isSuccess()) {
                    isConnected = true
                    log.info { "Successfully connected to MCP server" }
                } else {
                    throw MCPConnectionException("Failed to connect to MCP server: ${response.status}")
                }
            }
        }.onFailure { error ->
            log.error(error) { "Failed to connect to MCP server at $serverUrl" }
        }

    /**
     * Call a tool on the MCP server
     */
    suspend fun callTool(request: MCPToolRequest): Result<MCPToolResponse> =
        runCatching {
            if (!isConnected) {
                connect().getOrThrow()
            }

            log.debug { "Calling MCP tool: ${request.name} with params: ${request.arguments}" }

            val response =
                httpClient.post("$serverUrl/tools/call") {
                    contentType(ContentType.Application.Json)
                    setBody(request)
                }

            if (response.status.isSuccess()) {
                val toolResponse: MCPToolResponse = response.body()
                log.debug { "MCP tool call successful: ${toolResponse.content.size} content items" }
                toolResponse
            } else {
                val errorBody = response.body<String>()
                throw MCPToolException("Tool call failed: ${response.status} - $errorBody")
            }
        }.onFailure { error ->
            log.error(error) { "MCP tool call failed for ${request.name}" }
        }

    /**
     * List available tools from the MCP server
     */
    suspend fun listTools(): Result<List<MCPTool>> =
        runCatching {
            if (!isConnected) {
                connect().getOrThrow()
            }

            log.debug { "Listing available MCP tools" }

            val response = httpClient.get("$serverUrl/tools/list")

            if (response.status.isSuccess()) {
                val toolsResponse: MCPToolsListResponse = response.body()
                log.debug { "Retrieved ${toolsResponse.tools.size} available tools" }
                toolsResponse.tools
            } else {
                throw MCPToolException("Failed to list tools: ${response.status}")
            }
        }.onFailure { error ->
            log.error(error) { "Failed to list MCP tools" }
        }

    /**
     * Disconnect from the MCP server
     */
    suspend fun disconnect() {
        connectionMutex.withLock {
            if (isConnected) {
                log.info { "Disconnecting from MCP server" }
                httpClient.close()
                isConnected = false
            }
        }
    }

    /**
     * Check if connected to the MCP server
     */
    fun isConnected(): Boolean = isConnected
}

/**
 * Exception thrown when MCP connection fails
 */
class MCPConnectionException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * Exception thrown when MCP tool calls fail
 */
class MCPToolException(message: String, cause: Throwable? = null) : Exception(message, cause)
