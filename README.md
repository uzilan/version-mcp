# Maven Version MCP Server

A Model Context Protocol (MCP) server that provides dependency version management capabilities for Java projects using Maven and Gradle build systems.

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
- Playwright for web automation
- Kotlinx.serialization for JSON handling
- DOM4J for XML processing
- Ktor for HTTP client functionality
- Logback for logging

## Development

This project follows the MCP (Model Context Protocol) specification to provide tools for:

- Searching Maven dependencies
- Retrieving version information
- Updating project build files

Each component will be implemented incrementally following the task list in `.kiro/specs/maven-version-mcp-server/tasks.md`.