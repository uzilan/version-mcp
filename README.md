# Maven Version MCP Server

A Model Context Protocol (MCP) server that provides tools for searching Maven dependencies, retrieving version information, and updating Maven and Gradle project files.

## Features

- **Dependency Search**: Search for Maven dependencies on mvnrepository.com
- **Version Management**: Get latest versions and all available versions of dependencies
- **Project File Updates**: Update Maven (pom.xml) and Gradle (build.gradle) files
- **Comprehensive Error Handling**: Structured error responses, retry logic, and circuit breaker patterns
- **Structured Logging**: JSON-formatted logs with request tracking
- **Resource Management**: Automatic cleanup of MCP client connections
- **Health Monitoring**: Built-in health check and metrics endpoints
- **Flexible Configuration**: Support for config files, environment variables, and command-line arguments

## Quick Start

### Prerequisites

- Java 21 or higher
- Node.js (for Playwright MCP server)
- Maven or Gradle (for building)

### Installation

1. **Clone the repository**:
   ```bash
   git clone https://github.com/uzilan/version-mcp.git
   cd version-mcp
   ```

2. **Build the application**:
   ```bash
   ./gradlew build
   ```

3. **Run the server**:
   ```bash
   java -jar build/libs/maven-version-mcp-server-1.0.0.jar
   ```

### Using Startup Scripts

**Unix/Linux/macOS**:
```bash
./scripts/start-mcp-server.sh -j build/libs/maven-version-mcp-server-1.0.0.jar
```

**Windows**:
```cmd
scripts\start-mcp-server.bat -j build\libs\maven-version-mcp-server-1.0.0.jar
```

## Configuration

The server supports multiple configuration sources (in order of precedence):

1. Command line arguments
2. Environment variables
3. Configuration file (`config.json`)
4. Default values

### Configuration File

Create a `config.json` file in the working directory:

```json
{
  "logLevel": "INFO",
  "baseUrl": "https://mvnrepository.com",
  "maxRetries": 3,
  "retryDelayMs": 1000,
  "rateLimitDelayMs": 500,
  "circuitBreakerFailureThreshold": 5,
  "circuitBreakerRecoveryTimeoutMs": 60000,
  "requestTimeoutMs": 30000,
  "enableStructuredLogging": true,
  "logToFile": false,
  "logFile": "logs/maven-version-mcp-server.log",
  "enableMetrics": false,
  "metricsPort": 8081,
  "enableHealthCheck": true,
  "healthCheckPort": 8082
}
```

### Environment Variables

| Variable | Description | Default |
|----------|-------------|---------|
| `MCP_LOG_LEVEL` | Log level (DEBUG, INFO, WARN, ERROR) | INFO |
| `MCP_WORKING_DIR` | Working directory for MCP server | null |
| `MCP_BASE_URL` | Base URL for Maven repository | https://mvnrepository.com |
| `MCP_MAX_RETRIES` | Maximum retry attempts | 3 |
| `MCP_RETRY_DELAY` | Retry delay in milliseconds | 1000 |
| `MCP_RATE_LIMIT_DELAY` | Rate limiting delay in milliseconds | 500 |
| `MCP_CIRCUIT_BREAKER_THRESHOLD` | Circuit breaker failure threshold | 5 |
| `MCP_CIRCUIT_BREAKER_TIMEOUT` | Circuit breaker recovery timeout | 60000 |
| `MCP_REQUEST_TIMEOUT` | Request timeout in milliseconds | 30000 |
| `MCP_STRUCTURED_LOGGING` | Enable structured logging | true |
| `MCP_LOG_TO_FILE` | Enable file logging | false |
| `MCP_LOG_FILE` | Log file path | logs/maven-version-mcp-server.log |
| `MCP_ENABLE_METRICS` | Enable metrics collection | false |
| `MCP_METRICS_PORT` | Metrics server port | 8081 |
| `MCP_ENABLE_HEALTH_CHECK` | Enable health check endpoint | true |
| `MCP_HEALTH_CHECK_PORT` | Health check server port | 8082 |

### Command Line Arguments

```bash
java -jar maven-version-mcp-server.jar [options]

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
```

## MCP Tools

The server provides the following MCP tools:

### 1. `search_dependency`
Search for Maven dependencies on mvnrepository.com.

**Parameters**:
- `query` (string): Search query (e.g., "spring boot", "junit")

**Example**:
```json
{
  "name": "search_dependency",
  "arguments": {
    "query": "spring boot"
  }
}
```

### 2. `get_latest_version`
Get the latest version of a specific dependency.

**Parameters**:
- `groupId` (string): Maven group ID
- `artifactId` (string): Maven artifact ID

**Example**:
```json
{
  "name": "get_latest_version",
  "arguments": {
    "groupId": "org.springframework.boot",
    "artifactId": "spring-boot-starter"
  }
}
```

### 3. `get_all_versions`
Get all available versions of a specific dependency.

**Parameters**:
- `groupId` (string): Maven group ID
- `artifactId` (string): Maven artifact ID
- `limit` (number, optional): Maximum number of versions to return

**Example**:
```json
{
  "name": "get_all_versions",
  "arguments": {
    "groupId": "org.springframework.boot",
    "artifactId": "spring-boot-starter",
    "limit": 10
  }
}
```

### 4. `update_maven_dependency`
Update a dependency version in a Maven pom.xml file.

**Parameters**:
- `filePath` (string): Path to the pom.xml file
- `groupId` (string): Maven group ID
- `artifactId` (string): Maven artifact ID
- `newVersion` (string): New version to set

**Example**:
```json
{
  "name": "update_maven_dependency",
  "arguments": {
    "filePath": "pom.xml",
    "groupId": "org.springframework.boot",
    "artifactId": "spring-boot-starter",
    "newVersion": "3.2.0"
  }
}
```

### 5. `update_gradle_dependency`
Update a dependency version in a Gradle build.gradle file.

**Parameters**:
- `filePath` (string): Path to the build.gradle file
- `groupId` (string): Maven group ID
- `artifactId` (string): Maven artifact ID
- `newVersion` (string): New version to set
- `configuration` (string, optional): Gradle configuration (default: "implementation")

**Example**:
```json
{
  "name": "update_gradle_dependency",
  "arguments": {
    "filePath": "build.gradle",
    "groupId": "org.springframework.boot",
    "artifactId": "spring-boot-starter",
    "newVersion": "3.2.0",
    "configuration": "implementation"
  }
}
```

## Integration with Kiro

To integrate with Kiro, add the following configuration to your Kiro MCP settings:

### Basic Configuration

```json
{
  "mcpServers": {
    "maven-version": {
      "command": "java",
      "args": [
        "-jar",
        "maven-version-mcp-server.jar"
      ],
      "env": {
        "MCP_LOG_LEVEL": "INFO",
        "MCP_ENABLE_HEALTH_CHECK": "true"
      }
    }
  }
}
```

### Advanced Configuration

```json
{
  "mcpServers": {
    "maven-version": {
      "command": "java",
      "args": [
        "-jar",
        "maven-version-mcp-server.jar",
        "--log-level",
        "DEBUG",
        "--enable-metrics",
        "true"
      ],
      "env": {
        "MCP_LOG_LEVEL": "DEBUG",
        "MCP_WORKING_DIR": "/opt/mcp-server",
        "MCP_MAX_RETRIES": "5",
        "MCP_STRUCTURED_LOGGING": "true",
        "MCP_ENABLE_METRICS": "true",
        "MCP_METRICS_PORT": "8081"
      }
    }
  }
}
```

## Docker Deployment

### Using Docker Compose

1. **Create a configuration file**:
   ```bash
   cp config.json.example config.json
   # Edit config.json as needed
   ```

2. **Start the service**:
   ```bash
   docker-compose up -d
   ```

3. **Check logs**:
   ```bash
   docker-compose logs -f maven-version-mcp-server
   ```

### Using Docker directly

1. **Build the image**:
   ```bash
   docker build -t maven-version-mcp-server .
   ```

2. **Run the container**:
   ```bash
   docker run -d \
     --name maven-version-mcp-server \
     -p 8081:8081 \
     -p 8082:8082 \
     -v $(pwd)/config.json:/app/config.json:ro \
     -v $(pwd)/logs:/app/logs \
     maven-version-mcp-server
   ```

## Monitoring and Health Checks

### Health Check Endpoint

When health checks are enabled, the server provides a health check endpoint:

```bash
curl http://localhost:8082/health
```

Response:
```json
{
  "status": "healthy",
  "timestamp": "2024-01-15T10:30:00Z",
  "version": "1.0.0",
  "uptime": "PT1H30M"
}
```

### Metrics Endpoint

When metrics are enabled, the server provides a metrics endpoint:

```bash
curl http://localhost:8081/metrics
```

## Development

### Building from Source

1. **Clone the repository**:
   ```bash
   git clone https://github.com/uzilan/version-mcp.git
   cd version-mcp
   ```

2. **Build the project**:
   ```bash
   ./gradlew build
   ```

3. **Run tests**:
   ```bash
   ./gradlew test
   ```

4. **Check code style**:
   ```bash
   ./gradlew ktlintCheck
   ```

5. **Format code**:
   ```bash
   ./gradlew ktlintFormat
   ```

### Project Structure

```
src/
├── main/kotlin/com/mavenversion/mcp/
│   ├── client/          # MCP client implementation
│   ├── config/          # Configuration classes
│   ├── errors/          # Error handling
│   ├── files/           # File management (Maven/Gradle)
│   ├── logging/         # Structured logging
│   ├── models/          # Data models
│   ├── recovery/        # Error recovery mechanisms
│   ├── reliability/     # Reliability features
│   ├── server/          # MCP server implementation
│   ├── service/         # Application services
│   ├── tools/           # MCP tool implementations
│   └── web/             # Web scraping components
└── test/kotlin/         # Unit and integration tests
```

## Error Handling

The server implements comprehensive error handling:

- **Structured Error Responses**: All errors include detailed information
- **Retry Logic**: Automatic retry with exponential backoff
- **Circuit Breaker**: Prevents cascading failures
- **Resource Cleanup**: Automatic cleanup of MCP connections
- **Structured Logging**: JSON-formatted logs with request tracking

## Contributing

1. Fork the repository
2. Create a feature branch
3. Make your changes
4. Add tests for new functionality
5. Ensure all tests pass
6. Submit a pull request

## License

This project is licensed under the MIT License - see the LICENSE file for details.

## Support

For issues and questions:

1. Check the [Issues](https://github.com/uzilan/version-mcp/issues) page
2. Create a new issue with detailed information
3. Include logs and configuration details

## Changelog

### v1.0.0
- Initial release
- MCP server implementation
- Dependency search and version management
- Maven and Gradle file updates
- Comprehensive error handling
- Docker support
- Health checks and metrics