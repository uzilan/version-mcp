package com.mavenversion.mcp.cleanup

import com.mavenversion.mcp.client.MCPProcessManager
import com.mavenversion.mcp.client.PlaywrightMCPClient
import com.mavenversion.mcp.logging.StructuredLoggingService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import mu.KotlinLogging
import java.io.Closeable
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

private val log = KotlinLogging.logger {}

/**
 * Service for managing resource cleanup and lifecycle
 */
class ResourceCleanupService(
    private val loggingService: StructuredLoggingService,
) {
    private val resources = ConcurrentHashMap<String, ResourceInfo>()
    private val cleanupScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val isShuttingDown = AtomicBoolean(false)

    /**
     * Register a resource for cleanup
     */
    fun registerResource(
        id: String,
        resource: Closeable,
        priority: CleanupPriority = CleanupPriority.NORMAL,
        description: String = resource.javaClass.simpleName,
    ) {
        if (isShuttingDown.get()) {
            log.warn { "Attempting to register resource '$id' during shutdown" }
            return
        }

        resources[id] =
            ResourceInfo(
                resource = resource,
                priority = priority,
                description = description,
                registeredAt = System.currentTimeMillis(),
            )

        loggingService.logApplicationEvent(
            event = "RESOURCE_REGISTERED",
            details =
                mapOf(
                    "resourceId" to id,
                    "description" to description,
                    "priority" to priority.name,
                ).mapValues { it.value.toString() },
        )

        log.debug { "Registered resource '$id' for cleanup" }
    }

    /**
     * Unregister a resource
     */
    fun unregisterResource(id: String) {
        val resourceInfo = resources.remove(id)
        if (resourceInfo != null) {
            loggingService.logApplicationEvent(
                event = "RESOURCE_UNREGISTERED",
                details = mapOf("resourceId" to id),
            )
            log.debug { "Unregistered resource '$id'" }
        }
    }

    /**
     * Cleanup a specific resource
     */
    fun cleanupResource(id: String): Boolean {
        val resourceInfo = resources.remove(id)
        return if (resourceInfo != null) {
            try {
                resourceInfo.resource.close()
                loggingService.logApplicationEvent(
                    event = "RESOURCE_CLEANED_UP",
                    details =
                        mapOf(
                            "resourceId" to id,
                            "description" to resourceInfo.description,
                        ),
                )
                log.debug { "Successfully cleaned up resource '$id'" }
                true
            } catch (e: Exception) {
                loggingService.logApplicationEvent(
                    event = "RESOURCE_CLEANUP_FAILED",
                    details =
                        mapOf(
                            "resourceId" to id,
                            "error" to (e.message ?: "Unknown error"),
                        ),
                )
                log.error(e) { "Failed to cleanup resource '$id'" }
                false
            }
        } else {
            log.warn { "Resource '$id' not found for cleanup" }
            false
        }
    }

    /**
     * Cleanup all resources
     */
    fun cleanupAllResources() {
        if (isShuttingDown.compareAndSet(false, true)) {
            log.info { "Starting cleanup of all resources" }
            loggingService.logApplicationEvent(event = "CLEANUP_STARTED")

            // Cleanup resources by priority
            CleanupPriority.values().forEach { priority ->
                cleanupResourcesByPriority(priority)
            }

            loggingService.logApplicationEvent(event = "CLEANUP_COMPLETED")
            log.info { "Completed cleanup of all resources" }
        }
    }

    /**
     * Cleanup resources by priority
     */
    private fun cleanupResourcesByPriority(priority: CleanupPriority) {
        val resourcesToCleanup = resources.values.filter { it.priority == priority }

        if (resourcesToCleanup.isNotEmpty()) {
            log.info { "Cleaning up ${resourcesToCleanup.size} resources with priority ${priority.name}" }

            resourcesToCleanup.forEach { resourceInfo ->
                val resourceId = resources.entries.find { it.value == resourceInfo }?.key
                if (resourceId != null) {
                    cleanupResource(resourceId)
                }
            }
        }
    }

    /**
     * Cleanup resources asynchronously
     */
    fun cleanupAllResourcesAsync() {
        cleanupScope.launch {
            cleanupAllResources()
        }
    }

    /**
     * Get resource statistics
     */
    fun getResourceStats(): ResourceStats {
        val totalResources = resources.size
        val resourcesByPriority =
            resources.values.groupBy { it.priority }
                .mapValues { it.value.size } as Map<CleanupPriority, Int>

        return ResourceStats(
            totalResources = totalResources,
            resourcesByPriority = resourcesByPriority,
            isShuttingDown = isShuttingDown.get(),
        )
    }

    /**
     * Force cleanup with timeout
     */
    fun cleanupWithTimeout(timeoutMs: Long = 30000) {
        runBlocking {
            try {
                kotlinx.coroutines.withTimeout(timeoutMs) {
                    cleanupAllResources()
                }
            } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
                log.error { "Resource cleanup timed out after ${timeoutMs}ms" }
                loggingService.logApplicationEvent(
                    event = "CLEANUP_TIMEOUT",
                    details = mapOf("timeoutMs" to timeoutMs.toString()),
                )
            }
        }
    }

    /**
     * Shutdown the cleanup service
     */
    fun shutdown() {
        cleanupAllResources()
        cleanupScope.cancel()
        log.info { "Resource cleanup service shutdown" }
    }
}

/**
 * Resource information
 */
data class ResourceInfo(
    val resource: Closeable,
    val priority: CleanupPriority,
    val description: String,
    val registeredAt: Long,
)

/**
 * Cleanup priority levels
 */
enum class CleanupPriority {
    CRITICAL, // Must be cleaned up first (e.g., database connections)
    HIGH, // High priority (e.g., network connections)
    NORMAL, // Normal priority (e.g., file handles)
    LOW, // Low priority (e.g., temporary files)
}

/**
 * Resource statistics
 */
data class ResourceStats(
    val totalResources: Int,
    val resourcesByPriority: Map<CleanupPriority, Int>,
    val isShuttingDown: Boolean,
)

/**
 * Extension functions for common resource types
 */
fun ResourceCleanupService.registerMCPProcessManager(
    id: String,
    processManager: MCPProcessManager,
) {
    registerResource(
        id = id,
        resource =
            object : Closeable {
                override fun close() {
                    // MCPProcessManager cleanup logic would go here
                    // For now, just log that cleanup was called
                }
            },
        priority = CleanupPriority.HIGH,
        description = "MCP Process Manager",
    )
}

fun ResourceCleanupService.registerPlaywrightClient(
    id: String,
    playwrightClient: PlaywrightMCPClient,
) {
    registerResource(
        id = id,
        resource =
            object : Closeable {
                override fun close() {
                    // PlaywrightMCPClient cleanup logic would go here
                    // For now, just log that cleanup was called
                }
            },
        priority = CleanupPriority.HIGH,
        description = "Playwright MCP Client",
    )
}
