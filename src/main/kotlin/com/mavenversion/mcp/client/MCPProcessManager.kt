package com.mavenversion.mcp.client

import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import mu.KotlinLogging
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

private val log = KotlinLogging.logger {}

/**
 * Manages MCP server processes and their lifecycle
 */
class MCPProcessManager {
    private val processes = ConcurrentHashMap<String, ManagedMCPProcess>()
    private val processMutex = Mutex()

    /**
     * Get or create an MCP client for the given configuration
     */
    suspend fun getClient(config: MCPServerConfig): Result<StdioMCPClient> =
        runCatching {
            processMutex.withLock {
                val existing = processes[config.name]

                if (existing != null && existing.client.isConnected()) {
                    log.debug { "Reusing existing MCP client for ${config.name}" }
                    return@withLock existing.client
                }

                // Clean up existing process if it exists but is not connected
                existing?.let {
                    log.debug { "Cleaning up disconnected MCP process for ${config.name}" }
                    it.client.disconnect()
                    processes.remove(config.name)
                }

                // Create new client
                log.info { "Creating new MCP client for ${config.name}" }
                val client =
                    StdioMCPClient(
                        command = config.getFullCommand(),
                        workingDirectory = config.workingDirectory,
                    )

                val managedProcess =
                    ManagedMCPProcess(
                        config = config,
                        client = client,
                        restartAttempts = AtomicInteger(0),
                    )

                // Connect the client
                client.connect().getOrThrow()

                processes[config.name] = managedProcess
                log.info { "Successfully created and connected MCP client for ${config.name}" }

                client
            }
        }.onFailure { error ->
            log.error(error) { "Failed to get MCP client for ${config.name}" }
        }

    /**
     * Restart a specific MCP server
     */
    suspend fun restartServer(serverName: String): Result<Unit> =
        runCatching {
            processMutex.withLock {
                val managedProcess =
                    processes[serverName]
                        ?: throw MCPProcessException("No MCP server found with name: $serverName")

                if (managedProcess.restartAttempts.get() >= managedProcess.config.maxRestartAttempts) {
                    throw MCPProcessException("Maximum restart attempts exceeded for $serverName")
                }

                log.info { "Restarting MCP server: $serverName (attempt ${managedProcess.restartAttempts.incrementAndGet()})" }

                // Disconnect current client
                managedProcess.client.disconnect()

                // Wait before restart
                delay(managedProcess.config.restartDelayMs)

                // Restart the client
                managedProcess.client.restart().getOrThrow()

                log.info { "Successfully restarted MCP server: $serverName" }
            }
        }.onFailure { error ->
            log.error(error) { "Failed to restart MCP server: $serverName" }
        }

    /**
     * Stop a specific MCP server
     */
    suspend fun stopServer(serverName: String): Result<Unit> =
        runCatching {
            processMutex.withLock {
                val managedProcess =
                    processes.remove(serverName)
                        ?: throw MCPProcessException("No MCP server found with name: $serverName")

                log.info { "Stopping MCP server: $serverName" }
                managedProcess.client.disconnect()
                log.info { "Successfully stopped MCP server: $serverName" }
            }
        }.onFailure { error ->
            log.error(error) { "Failed to stop MCP server: $serverName" }
        }

    /**
     * Stop all MCP servers
     */
    suspend fun stopAll() {
        processMutex.withLock {
            log.info { "Stopping all MCP servers (${processes.size} servers)" }

            processes.values.forEach { managedProcess ->
                try {
                    managedProcess.client.disconnect()
                } catch (e: Exception) {
                    log.warn(e) { "Error stopping MCP server ${managedProcess.config.name}" }
                }
            }

            processes.clear()
            log.info { "All MCP servers stopped" }
        }
    }

    /**
     * Get status of all managed processes
     */
    fun getStatus(): Map<String, MCPServerStatus> {
        return processes.mapValues { (_, managedProcess) ->
            MCPServerStatus(
                name = managedProcess.config.name,
                isConnected = managedProcess.client.isConnected(),
                restartAttempts = managedProcess.restartAttempts.get(),
                maxRestartAttempts = managedProcess.config.maxRestartAttempts,
            )
        }
    }

    /**
     * Check if a server is healthy and restart if needed
     */
    suspend fun healthCheck(serverName: String): Result<Boolean> =
        runCatching {
            val managedProcess =
                processes[serverName]
                    ?: return@runCatching false

            val isHealthy = managedProcess.client.isConnected()

            if (!isHealthy && managedProcess.config.autoRestart) {
                log.warn { "MCP server $serverName is unhealthy, attempting restart" }
                restartServer(serverName).getOrThrow()
                managedProcess.client.isConnected()
            } else {
                isHealthy
            }
        }
}

/**
 * Represents a managed MCP process
 */
private data class ManagedMCPProcess(
    val config: MCPServerConfig,
    val client: StdioMCPClient,
    val restartAttempts: AtomicInteger,
)

/**
 * Status information for an MCP server
 */
data class MCPServerStatus(
    val name: String,
    val isConnected: Boolean,
    val restartAttempts: Int,
    val maxRestartAttempts: Int,
)

/**
 * Exception thrown when MCP process management fails
 */
class MCPProcessException(message: String, cause: Throwable? = null) : Exception(message, cause)
