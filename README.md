# Maven Version MCP Server

A Model Context Protocol (MCP) server that provides dependency version management capabilities for Java projects using Maven and Gradle build systems. The server uses stdio-based MCP communication and integrates with Playwright MCP server for reliable web scraping of mvnrepository.com.

## Project Structure

```
src/
├── main/kotlin/com/mavenversion/mcp/
│   ├── Main.kt                    # Application entry point
│   ├── server/                    # MCP server core components
│   ├── models/                    # Data models
│   ├── web/                       # Web scraping components
│   ├── files/                     # File management components
│   └── tools/                     # MCP tool implementations
└── test/kotlin/com/mavenversion/mcp/
    └── ...                        # Test files
```

## Building

```bash
./gradlew build
```

## Running

```bash
./gradlew run
```

## Dependencies

- Kotlin 2.0.0
- Playwright MCP Server (via uvx or npx)
- Kotlinx.serialization for JSON handling
- DOM4J for XML processing
- Process management for MCP server communication
- Logback for logging

## Prerequisites

To use the Playwright MCP integration, you need either:

### Option 1: uvx (Recommended)
Install uv package manager: https://docs.astral.sh/uv/getting-started/installation/

### Option 2: npx
Install Node.js and npm

The system will automatically manage the Playwright MCP server process.

## Configuration

See [MCP Configuration Guide](docs/mcp-configuration.md) for detailed information on configuring the stdio-based MCP client architecture.

## Development

This project follows the MCP (Model Context Protocol) specification to provide tools for:

- Searching Maven dependencies
- Retrieving version information
- Updating project build files

### Architecture

The server uses a stdio-based MCP architecture:
- **MCP Server**: Exposes tools via MCP protocol
- **MCP Client**: Communicates with Playwright MCP server via subprocess
- **Process Management**: Automatic lifecycle management of MCP server processes
- **Reliability**: Circuit breakers, retries, and health checks

Each component is implemented incrementally following the task list in `.kiro/specs/maven-version-mcp-server/tasks.md`.

## Testing

Run all tests:
```bash
./gradlew test
```

Run integration tests (requires uvx or npx):
```bash
INTEGRATION_TESTS=true ./gradlew test --tests "*IntegrationTest"
```