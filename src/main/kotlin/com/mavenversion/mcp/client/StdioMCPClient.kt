package com.mavenversion.mcp.client

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement
import mu.KotlinLogging
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.util.concurrent.atomic.AtomicLong

private val log = KotlinLogging.logger {}

/**
 * Stdio-based MCP client that communicates with MCP servers via subprocess
 */
class StdioMCPClient(
    private val command: List<String>,
    private val workingDirectory: String? = null
) {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }
    
    private val connectionMutex = Mutex()
    private val requestIdCounter = AtomicLong(1)
    
    private var process: Process? = null
    private var writer: BufferedWriter? = null
    private var reader: BufferedReader? = null
    private var isConnected = false
    
    /**
     * Start the MCP server process and establish connection
     */
    suspend fun connect(): Result<Unit> = runCatching {
        connectionMutex.withLock {
            if (isConnected) {
                log.debug { "Already connected to MCP server" }
                return@withLock
            }
            
            log.info { "Starting MCP server process: ${command.joinToString(" ")}" }
            
            val processBuilder = ProcessBuilder(command)
            workingDirectory?.let { processBuilder.directory(java.io.File(it)) }
            
            // Redirect stderr to inherit so we can see server logs
            processBuilder.redirectError(ProcessBuilder.Redirect.INHERIT)
            
            process = processBuilder.start()
            
            writer = BufferedWriter(OutputStreamWriter(process!!.outputStream))
            reader = BufferedReader(InputStreamReader(process!!.inputStream))
            
            // Perform MCP handshake
            performHandshake()
            
            isConnected = true
            log.info { "Successfully connected to MCP server" }
        }
    }.onFailure { error ->
        log.error(error) { "Failed to connect to MCP server" }
        cleanup()
    }
    
    /**
     * Perform MCP protocol handshake
     */
    private suspend fun performHandshake() {
        log.debug { "Performing MCP handshake" }
        
        // Send initialize request
        val initializeRequest = buildJsonObject {
            put("jsonrpc", JsonPrimitive("2.0"))
            put("id", JsonPrimitive(requestIdCounter.getAndIncrement()))
            put("method", JsonPrimitive("initialize"))
            put("params", buildJsonObject {
                put("protocolVersion", JsonPrimitive("2024-11-05"))
                put("capabilities", buildJsonObject {
                    put("tools", buildJsonObject {})
                })
                put("clientInfo", buildJsonObject {
                    put("name", JsonPrimitive("maven-version-mcp-server"))
                    put("version", JsonPrimitive("1.0.0"))
                })
            })
        }
        
        sendMessage(initializeRequest)
        val response = receiveMessage()
        
        log.debug { "Initialize response: $response" }
        
        // Send initialized notification
        val initializedNotification = buildJsonObject {
            put("jsonrpc", JsonPrimitive("2.0"))
            put("method", JsonPrimitive("notifications/initialized"))
        }
        
        sendMessage(initializedNotification)
        log.debug { "MCP handshake completed" }
    }
    
    /**
     * Call a tool on the MCP server
     */
    suspend fun callTool(request: MCPToolRequest): Result<MCPToolResponse> = runCatching {
        if (!isConnected) {
            connect().getOrThrow()
        }
        
        log.debug { "Calling MCP tool: ${request.name} with params: ${request.arguments}" }
        
        val requestId = requestIdCounter.getAndIncrement()
        val mcpRequest = buildJsonObject {
            put("jsonrpc", JsonPrimitive("2.0"))
            put("id", JsonPrimitive(requestId))
            put("method", JsonPrimitive("tools/call"))
            put("params", buildJsonObject {
                put("name", JsonPrimitive(request.name))
                put("arguments", json.encodeToJsonElement(request.arguments))
            })
        }
        
        sendMessage(mcpRequest)
        val response = receiveMessage()
        
        // Parse response
        val responseObj = response as? JsonObject 
            ?: throw MCPProtocolException("Invalid response format")
            
        val error = responseObj["error"]
        if (error != null) {
            throw MCPToolException("Tool call failed: $error")
        }
        
        val result = responseObj["result"] as? JsonObject
            ?: throw MCPProtocolException("Missing result in response")
            
        val toolResponse = json.decodeFromJsonElement<MCPToolResponse>(result)
        log.debug { "MCP tool call successful: ${toolResponse.content.size} content items" }
        
        toolResponse
    }.onFailure { error ->
        log.error(error) { "MCP tool call failed for ${request.name}" }
    }
    
    /**
     * List available tools from the MCP server
     */
    suspend fun listTools(): Result<List<MCPTool>> = runCatching {
        if (!isConnected) {
            connect().getOrThrow()
        }
        
        log.debug { "Listing available MCP tools" }
        
        val requestId = requestIdCounter.getAndIncrement()
        val mcpRequest = buildJsonObject {
            put("jsonrpc", JsonPrimitive("2.0"))
            put("id", JsonPrimitive(requestId))
            put("method", JsonPrimitive("tools/list"))
        }
        
        sendMessage(mcpRequest)
        val response = receiveMessage()
        
        // Parse response
        val responseObj = response as? JsonObject 
            ?: throw MCPProtocolException("Invalid response format")
            
        val error = responseObj["error"]
        if (error != null) {
            throw MCPToolException("List tools failed: $error")
        }
        
        val result = responseObj["result"] as? JsonObject
            ?: throw MCPProtocolException("Missing result in response")
            
        val toolsArray = result["tools"] 
            ?: throw MCPProtocolException("Missing tools in response")
            
        val tools = json.decodeFromJsonElement<List<MCPTool>>(toolsArray)
        log.debug { "Retrieved ${tools.size} available tools" }
        
        tools
    }.onFailure { error ->
        log.error(error) { "Failed to list MCP tools" }
    }
    
    /**
     * Send a JSON-RPC message to the server
     */
    private suspend fun sendMessage(message: JsonObject) = withContext(Dispatchers.IO) {
        val messageStr = json.encodeToString(JsonObject.serializer(), message)
        log.trace { "Sending MCP message: $messageStr" }
        
        writer?.let { w ->
            w.write(messageStr)
            w.newLine()
            w.flush()
        } ?: throw MCPProtocolException("Writer not available")
    }
    
    /**
     * Receive a JSON-RPC message from the server
     */
    private suspend fun receiveMessage(): JsonElement = withContext(Dispatchers.IO) {
        val line = reader?.readLine() 
            ?: throw MCPProtocolException("Reader not available or connection closed")
            
        log.trace { "Received MCP message: $line" }
        
        json.parseToJsonElement(line)
    }
    
    /**
     * Disconnect from the MCP server
     */
    suspend fun disconnect() {
        connectionMutex.withLock {
            if (isConnected) {
                log.info { "Disconnecting from MCP server" }
                cleanup()
                isConnected = false
            }
        }
    }
    
    /**
     * Clean up resources
     */
    private fun cleanup() {
        try {
            writer?.close()
            reader?.close()
            process?.destroyForcibly()
        } catch (e: Exception) {
            log.warn(e) { "Error during cleanup" }
        }
        
        writer = null
        reader = null
        process = null
    }
    
    /**
     * Check if connected to the MCP server
     */
    fun isConnected(): Boolean = isConnected && process?.isAlive == true
    
    /**
     * Restart the connection if it fails
     */
    suspend fun restart(): Result<Unit> = runCatching {
        log.info { "Restarting MCP server connection" }
        disconnect()
        connect().getOrThrow()
    }
}

/**
 * Exception thrown when MCP protocol communication fails
 */
class MCPProtocolException(message: String, cause: Throwable? = null) : Exception(message, cause)