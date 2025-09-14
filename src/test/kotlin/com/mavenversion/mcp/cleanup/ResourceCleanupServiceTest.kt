package com.mavenversion.mcp.cleanup

import com.mavenversion.mcp.logging.StructuredLoggingService
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.io.Closeable
import java.io.IOException

class ResourceCleanupServiceTest {
    private lateinit var loggingService: StructuredLoggingService
    private lateinit var resourceCleanupService: ResourceCleanupService

    @BeforeEach
    fun setUp() {
        loggingService = mockk(relaxed = true)
        resourceCleanupService = ResourceCleanupService(loggingService)
    }

    @Nested
    @DisplayName("Resource Registration Tests")
    inner class ResourceRegistrationTests {
        @Test
        @DisplayName("Should register resource successfully")
        fun shouldRegisterResourceSuccessfully() {
            // Given
            val resource = mockk<Closeable>(relaxed = true)

            // When
            resourceCleanupService.registerResource(
                id = "test_resource",
                resource = resource,
                priority = CleanupPriority.HIGH,
                description = "Test Resource",
            )

            // Then
            val stats = resourceCleanupService.getResourceStats()
            assertThat(stats.totalResources).isEqualTo(1)
            assertThat(stats.resourcesByPriority[CleanupPriority.HIGH]).isEqualTo(1)
        }

        @Test
        @DisplayName("Should register multiple resources with different priorities")
        fun shouldRegisterMultipleResourcesWithDifferentPriorities() {
            // Given
            val resource1 = mockk<Closeable>(relaxed = true)
            val resource2 = mockk<Closeable>(relaxed = true)
            val resource3 = mockk<Closeable>(relaxed = true)

            // When
            resourceCleanupService.registerResource("resource1", resource1, CleanupPriority.CRITICAL)
            resourceCleanupService.registerResource("resource2", resource2, CleanupPriority.HIGH)
            resourceCleanupService.registerResource("resource3", resource3, CleanupPriority.NORMAL)

            // Then
            val stats = resourceCleanupService.getResourceStats()
            assertThat(stats.totalResources).isEqualTo(3)
            assertThat(stats.resourcesByPriority[CleanupPriority.CRITICAL]).isEqualTo(1)
            assertThat(stats.resourcesByPriority[CleanupPriority.HIGH]).isEqualTo(1)
            assertThat(stats.resourcesByPriority[CleanupPriority.NORMAL]).isEqualTo(1)
        }

        @Test
        @DisplayName("Should unregister resource successfully")
        fun shouldUnregisterResourceSuccessfully() {
            // Given
            val resource = mockk<Closeable>(relaxed = true)
            resourceCleanupService.registerResource("test_resource", resource)

            // When
            resourceCleanupService.unregisterResource("test_resource")

            // Then
            val stats = resourceCleanupService.getResourceStats()
            assertThat(stats.totalResources).isEqualTo(0)
        }

        @Test
        @DisplayName("Should handle unregistering non-existent resource")
        fun shouldHandleUnregisteringNonExistentResource() {
            // When
            resourceCleanupService.unregisterResource("non_existent")

            // Then
            val stats = resourceCleanupService.getResourceStats()
            assertThat(stats.totalResources).isEqualTo(0)
        }
    }

    @Nested
    @DisplayName("Resource Cleanup Tests")
    inner class ResourceCleanupTests {
        @Test
        @DisplayName("Should cleanup specific resource successfully")
        fun shouldCleanupSpecificResourceSuccessfully() {
            // Given
            val resource = mockk<Closeable>(relaxed = true)
            resourceCleanupService.registerResource("test_resource", resource)

            // When
            val result = resourceCleanupService.cleanupResource("test_resource")

            // Then
            assertThat(result).isTrue()
            verify { resource.close() }
            val stats = resourceCleanupService.getResourceStats()
            assertThat(stats.totalResources).isEqualTo(0)
        }

        @Test
        @DisplayName("Should handle cleanup failure gracefully")
        fun shouldHandleCleanupFailureGracefully() {
            // Given
            val resource = mockk<Closeable>()
            every { resource.close() } throws IOException("Cleanup failed")
            resourceCleanupService.registerResource("test_resource", resource)

            // When
            val result = resourceCleanupService.cleanupResource("test_resource")

            // Then
            assertThat(result).isFalse()
            verify { resource.close() }
        }

        @Test
        @DisplayName("Should cleanup all resources by priority")
        fun shouldCleanupAllResourcesByPriority() {
            // Given
            val criticalResource = mockk<Closeable>(relaxed = true)
            val highResource = mockk<Closeable>(relaxed = true)
            val normalResource = mockk<Closeable>(relaxed = true)

            resourceCleanupService.registerResource("critical", criticalResource, CleanupPriority.CRITICAL)
            resourceCleanupService.registerResource("high", highResource, CleanupPriority.HIGH)
            resourceCleanupService.registerResource("normal", normalResource, CleanupPriority.NORMAL)

            // When
            resourceCleanupService.cleanupAllResources()

            // Then
            verify { criticalResource.close() }
            verify { highResource.close() }
            verify { normalResource.close() }
            val stats = resourceCleanupService.getResourceStats()
            assertThat(stats.totalResources).isEqualTo(0)
        }

        @Test
        @DisplayName("Should handle cleanup of non-existent resource")
        fun shouldHandleCleanupOfNonExistentResource() {
            // When
            val result = resourceCleanupService.cleanupResource("non_existent")

            // Then
            assertThat(result).isFalse()
        }
    }

    @Nested
    @DisplayName("Statistics Tests")
    inner class StatisticsTests {
        @Test
        @DisplayName("Should provide accurate resource statistics")
        fun shouldProvideAccurateResourceStatistics() {
            // Given
            val resource1 = mockk<Closeable>(relaxed = true)
            val resource2 = mockk<Closeable>(relaxed = true)
            val resource3 = mockk<Closeable>(relaxed = true)

            resourceCleanupService.registerResource("resource1", resource1, CleanupPriority.CRITICAL)
            resourceCleanupService.registerResource("resource2", resource2, CleanupPriority.HIGH)
            resourceCleanupService.registerResource("resource3", resource3, CleanupPriority.NORMAL)

            // When
            val stats = resourceCleanupService.getResourceStats()

            // Then
            assertThat(stats.totalResources).isEqualTo(3)
            assertThat(stats.resourcesByPriority[CleanupPriority.CRITICAL]).isEqualTo(1)
            assertThat(stats.resourcesByPriority[CleanupPriority.HIGH]).isEqualTo(1)
            assertThat(stats.resourcesByPriority[CleanupPriority.NORMAL]).isEqualTo(1)
            assertThat(stats.isShuttingDown).isFalse()
        }

        @Test
        @DisplayName("Should track shutdown state")
        fun shouldTrackShutdownState() {
            // Given
            val resource = mockk<Closeable>(relaxed = true)
            resourceCleanupService.registerResource("test_resource", resource)

            // When
            resourceCleanupService.cleanupAllResources()

            // Then
            val stats = resourceCleanupService.getResourceStats()
            assertThat(stats.isShuttingDown).isTrue()
        }
    }

    @Nested
    @DisplayName("Extension Function Tests")
    inner class ExtensionFunctionTests {
        @Test
        @DisplayName("Should register MCP process manager")
        fun shouldRegisterMCPProcessManager() {
            // Given
            val processManager = mockk<com.mavenversion.mcp.client.MCPProcessManager>(relaxed = true)

            // When
            resourceCleanupService.registerMCPProcessManager("mcp_manager", processManager)

            // Then
            val stats = resourceCleanupService.getResourceStats()
            assertThat(stats.totalResources).isEqualTo(1)
            assertThat(stats.resourcesByPriority[CleanupPriority.HIGH]).isEqualTo(1)
        }

        @Test
        @DisplayName("Should register Playwright client")
        fun shouldRegisterPlaywrightClient() {
            // Given
            val playwrightClient = mockk<com.mavenversion.mcp.client.PlaywrightMCPClient>(relaxed = true)

            // When
            resourceCleanupService.registerPlaywrightClient("playwright_client", playwrightClient)

            // Then
            val stats = resourceCleanupService.getResourceStats()
            assertThat(stats.totalResources).isEqualTo(1)
            assertThat(stats.resourcesByPriority[CleanupPriority.HIGH]).isEqualTo(1)
        }
    }

    @Nested
    @DisplayName("Timeout Tests")
    inner class TimeoutTests {
        @Test
        @DisplayName("Should cleanup with timeout")
        fun shouldCleanupWithTimeout() {
            // Given
            val resource = mockk<Closeable>(relaxed = true)
            resourceCleanupService.registerResource("test_resource", resource)

            // When
            resourceCleanupService.cleanupWithTimeout(1000)

            // Then
            verify { resource.close() }
            val stats = resourceCleanupService.getResourceStats()
            assertThat(stats.totalResources).isEqualTo(0)
        }
    }
}
