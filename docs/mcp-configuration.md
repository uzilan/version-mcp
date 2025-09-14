# MCP Server Configuration Guide

This document explains how to configure and use the stdio-based MCP client architecture in the Maven Version MCP Server.

## Overview

The Maven Version MCP Server now uses stdio-based communication with MCP servers, which is the standard way MCP servers communicate in production. This replaces the previous HTTP-based approach and provides better process management and reliability.

## Configuration

### Default Playwright MCP Server Configurations

The system provides two default configurations for the Playwright MCP server:

#### Using npx (Default)
```kotlin
val config = MCPServerConfig.playwrightDefault()
// Equivalent to:
val config = MCPServerConfig(
    name = "playwright",
    command = listOf("npx", "@playwright/mcp"),
    env = mapOf("NODE_ENV" to "production")
)
```

#### Alternative: Using uvx
```kotlin
val config = MCPServerConfig(
    name = "playwright",
    command = listOf("uvx", "playwright-mcp-server"),
    env = mapOf("FASTMCP_LOG_LEVEL" to "ERROR")
)
```

### Custom Configuration

You can create custom MCP server configurations:

```kotlin
val customConfig = MCPServerConfig(
    name = "my-custom-server",
    command = listOf("python", "-m", "my_mcp_server"),
    args = listOf("--port", "8080"),
    env = mapOf(
        "LOG_LEVEL" to "DEBUG",
        "API_KEY" to "your-api-key"
    ),
    workingDirectory = "/path/to/server",
    autoRestart = true,
    maxRestartAttempts = 3,
    restartDelayMs = 1000
)
```

## Usage

### Basic Usage

```kotlin
import com.mavenversion.mcp.client.*

// Create process manager
val processManager = MCPProcessManager()

// Create Playwright client with default config
val config = MCPServerConfig.playwrightDefault()
val playwrightClient = PlaywrightMCPClient(config, processManager)

try {
    // Connect to the server
    playwrightClient.connect().getOrThrow()
    
    // Use the client
    val result = playwrightClient.navigateToUrl("https://example.com")
    
} finally {
    // Cleanup
    processManager.stopAll()
}
```

### Using with MavenRepositoryClient

```kotlin
val config = MCPServerConfig.playwrightDefault()
val processManager = MCPProcessManager()
val playwrightClient = PlaywrightMCPClient(config, processManager)
val mavenClient = MavenRepositoryClient(playwrightClient)

try {
    mavenClient.initialize().getOrThrow()
    
    val searchResults = mavenClient.searchDependencies("spring-boot")
    // Process results...
    
} finally {
    mavenClient.close()
    processManager.stopAll()
}
```

## Process Management

### Health Checks and Restart

The process manager automatically handles server health checks and restarts:

```kotlin
val processManager = MCPProcessManager()

// Check if a server is healthy
val isHealthy = processManager.healthCheck("playwright").getOrElse { false }

// Manually restart a server
processManager.restartServer("playwright")

// Get status of all servers
val status = processManager.getStatus()
status.forEach { (name, serverStatus) ->
    println("Server $name: connected=${serverStatus.isConnected}, restarts=${serverStatus.restartAttempts}")
}
```

### Multiple Servers

You can manage multiple MCP servers simultaneously:

```kotlin
val processManager = MCPProcessManager()

val playwrightConfig = MCPServerConfig.playwrightDefault()
val customConfig = MCPServerConfig(
    name = "custom-server",
    command = listOf("my-server"),
    autoRestart = true
)

val playwrightClient = processManager.getClient(playwrightConfig).getOrThrow()
val customClient = processManager.getClient(customConfig).getOrThrow()

// Both servers are managed independently
```

## Prerequisites

### For Playwright MCP Server

#### Option 1: Using npx (Default)
1. Install Node.js and npm
2. The server will be downloaded automatically when first used

#### Option 2: Using uvx
1. Install uv: https://docs.astral.sh/uv/getting-started/installation/
2. uvx will automatically download and run the Playwright MCP server

### Verification

You can verify your setup by running the integration tests:

```bash
INTEGRATION_TESTS=true ./gradlew test --tests "StdioMCPIntegrationTest"
```

## Error Handling

The stdio-based architecture provides robust error handling:

- **Process Failures**: Automatic process restart with configurable retry limits
- **Communication Errors**: Proper cleanup and error reporting
- **Connection Issues**: Health checks and automatic recovery
- **Resource Management**: Automatic cleanup of processes and streams

## Migration from HTTP-based Client

If you were using the old HTTP-based client:

### Before (HTTP-based)
```kotlin
val playwrightClient = PlaywrightMCPClient("http://localhost:3000")
```

### After (stdio-based)
```kotlin
val config = MCPServerConfig.playwrightDefault() // Uses npx @playwright/mcp
val processManager = MCPProcessManager()
val playwrightClient = PlaywrightMCPClient(config, processManager)
```

The API remains the same, but you need to:
1. Create a configuration object
2. Create a process manager
3. Pass both to the client constructor
4. Remember to call `processManager.stopAll()` for cleanup

## Troubleshooting

### Common Issues

1. **"Command not found" errors**: Make sure npx/npm is installed (or uvx/uv if using alternative configuration)
2. **Permission errors**: Check that the MCP server executable has proper permissions
3. **Port conflicts**: The stdio-based approach doesn't use ports, so this shouldn't be an issue
4. **Process hanging**: Check the server logs and ensure proper cleanup in finally blocks
5. **Wait functionality**: Currently simplified - the MCP server's `browser_wait_for` expects text content, not CSS selectors

## Current Status

✅ **Successfully Implemented**: The stdio-based MCP client architecture is fully functional with the `@playwright/mcp` package:

- **Connection**: ✅ Successfully connects to Playwright MCP server via npx
- **Navigation**: ✅ `browser_navigate` works perfectly for web page navigation  
- **Content Retrieval**: ✅ `browser_evaluate` works for getting page content and element text
- **Typing**: ✅ `browser_type` works for filling form fields
- **Clicking**: ✅ `browser_click` works for element interaction

**Note**: The `waitForElement` functionality is currently simplified since the `browser_wait_for` tool expects text content rather than CSS selectors. Navigation and content retrieval are the core functionalities and work perfectly.

### Debugging

Enable debug logging to see detailed process management information:

```kotlin
// Set log level to DEBUG in logback.xml
// Or use environment variable
System.setProperty("LOG_LEVEL", "DEBUG")
```

### Testing Without Real Servers

The integration tests are designed to work without real MCP servers installed. They will attempt to connect and handle failures gracefully, which tests the error handling paths.