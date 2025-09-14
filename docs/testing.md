# Testing Guide

This document provides comprehensive information about testing the Maven Version MCP Server.

## Test Structure

The test suite is organized into several categories:

### Unit Tests
- **Location**: `src/test/kotlin/com/mavenversion/mcp/`
- **Purpose**: Test individual components in isolation
- **Execution**: Fast, no external dependencies
- **Examples**: `SearchDependencyToolTest`, `MavenFileManagerTest`, `ErrorRecoveryServiceTest`

### Integration Tests
- **Location**: `src/test/kotlin/com/mavenversion/mcp/integration/`
- **Purpose**: Test component interactions and workflows
- **Execution**: May require external services
- **Examples**: `MCPIntegrationTest`, `ApplicationIntegrationTest`

### Live Integration Tests
- **Location**: `src/test/kotlin/com/mavenversion/mcp/integration/LiveMavenRepositoryIntegrationTest.kt`
- **Purpose**: Test against live mvnrepository.com
- **Execution**: Requires `INTEGRATION_TESTS=true` environment variable
- **Rate Limiting**: Built-in delays to respect server limits

### Performance Tests
- **Location**: `src/test/kotlin/com/mavenversion/mcp/integration/PerformanceIntegrationTest.kt`
- **Purpose**: Test concurrent request handling and performance
- **Execution**: Requires `INTEGRATION_TESTS=true` environment variable
- **Metrics**: Measures execution time and success rates

### End-to-End Tests
- **Location**: `src/test/kotlin/com/mavenversion/mcp/integration/EndToEndWorkflowTest.kt`
- **Purpose**: Test complete workflows from MCP call to file modification
- **Execution**: Uses temporary files and mocked MCP responses
- **Coverage**: Full dependency management workflows

### Mock Tests
- **Location**: `src/test/kotlin/com/mavenversion/mcp/integration/MockMCPServerTest.kt`
- **Purpose**: Test error handling and edge cases with mocked responses
- **Execution**: Fast, reliable for CI/CD
- **Coverage**: All error scenarios and edge cases

## Running Tests

### All Tests
```bash
./gradlew test
```

### Unit Tests Only
```bash
./gradlew unitTest
```

### Integration Tests Only
```bash
./gradlew integrationTest
```

### Tests with Coverage
```bash
./gradlew testWithCoverage
```

### Validate Coverage
```bash
./gradlew validateCoverage
```

### Specific Test Class
```bash
./gradlew test --tests "SearchDependencyToolTest"
```

### Specific Test Method
```bash
./gradlew test --tests "SearchDependencyToolTest.shouldSearchForDependencies"
```

## Test Configuration

### Environment Variables

| Variable | Description | Default | Required For |
|----------|-------------|---------|--------------|
| `INTEGRATION_TESTS` | Enable live integration tests | `false` | Live tests |
| `MCP_LOG_LEVEL` | Log level for tests | `WARN` | All tests |
| `MCP_BASE_URL` | Base URL for testing | `https://mvnrepository.com` | Live tests |

### Test Profiles

#### Unit Test Profile
- **Purpose**: Fast, reliable tests for CI/CD
- **Execution**: Parallel execution enabled
- **Coverage**: Individual components
- **Dependencies**: None

#### Integration Test Profile
- **Purpose**: Component interaction testing
- **Execution**: Sequential execution
- **Coverage**: Workflow testing
- **Dependencies**: Mocked services

#### Live Test Profile
- **Purpose**: Real-world testing
- **Execution**: Sequential with rate limiting
- **Coverage**: Live service integration
- **Dependencies**: Live mvnrepository.com

## Test Coverage

### Coverage Requirements
- **Minimum Coverage**: 80%
- **Report Formats**: HTML, XML
- **Coverage Tool**: JaCoCo
- **Validation**: Automatic on test completion

### Coverage Reports
- **HTML Report**: `build/reports/jacoco/test/html/index.html`
- **XML Report**: `build/reports/jacoco/test/jacocoTestReport.xml`
- **CSV Report**: Disabled (not needed)

### Coverage Exclusions
- Generated code
- Test classes
- Main application entry point
- Configuration classes

## Test Data

### Sample Files
- **Maven POM**: `src/test/resources/sample-pom.xml`
- **Gradle Build**: `src/test/resources/sample-build.gradle`
- **HTML Responses**: Embedded in test classes

### Mock Responses
- **Search Results**: Realistic HTML from mvnrepository.com
- **Version Information**: Structured version data
- **Error Responses**: Various error scenarios

## Performance Testing

### Metrics Collected
- **Execution Time**: Per test and per operation
- **Success Rate**: Percentage of successful operations
- **Concurrent Requests**: Handling of multiple simultaneous requests
- **Rate Limiting**: Respect for server limits

### Performance Benchmarks
- **Unit Tests**: < 1 second per test
- **Integration Tests**: < 30 seconds per test
- **Live Tests**: < 60 seconds per test
- **Concurrent Tests**: < 2 minutes for high load

## Error Testing

### Error Scenarios Tested
- **Connection Failures**: Network connectivity issues
- **Timeout Scenarios**: Request timeouts
- **Malformed Responses**: Invalid HTML/JSON
- **Rate Limiting**: Server rate limit responses
- **Resource Exhaustion**: Memory and file handle limits

### Error Recovery Testing
- **Retry Logic**: Exponential backoff
- **Circuit Breaker**: Failure threshold handling
- **Graceful Degradation**: Partial failure handling
- **Resource Cleanup**: Proper cleanup on errors

## CI/CD Integration

### GitHub Actions
```yaml
- name: Run Unit Tests
  run: ./gradlew unitTest

- name: Run Integration Tests
  run: ./gradlew integrationTest
  env:
    INTEGRATION_TESTS: true

- name: Generate Coverage Report
  run: ./gradlew testWithCoverage

- name: Validate Coverage
  run: ./gradlew validateCoverage
```

### Test Reports
- **JUnit XML**: `build/test-results/test/TEST-*.xml`
- **HTML Report**: `build/reports/tests/test/index.html`
- **Coverage Report**: `build/reports/jacoco/test/html/index.html`

## Debugging Tests

### Test Logging
```bash
# Enable debug logging
./gradlew test --info

# Enable trace logging
./gradlew test --debug

# Show test output
./gradlew test --info --tests "SearchDependencyToolTest"
```

### Test Isolation
- Each test runs in isolation
- Temporary files are cleaned up
- MCP connections are properly closed
- Resources are released

### Test Data Cleanup
- Temporary files: Automatically deleted
- MCP processes: Terminated after tests
- Network connections: Closed properly
- Memory: Garbage collected

## Best Practices

### Writing Tests
1. **Use descriptive test names** that explain what is being tested
2. **Follow AAA pattern**: Arrange, Act, Assert
3. **Test one thing per test method**
4. **Use appropriate assertions** for the expected behavior
5. **Clean up resources** in `@AfterEach` methods

### Test Organization
1. **Group related tests** in the same test class
2. **Use nested classes** for test organization
3. **Use `@DisplayName`** for readable test names
4. **Use `@Tag`** for test categorization

### Mock Usage
1. **Mock external dependencies** in unit tests
2. **Use realistic mock data** that matches real responses
3. **Verify mock interactions** when important
4. **Don't over-mock** - test real behavior when possible

### Integration Testing
1. **Use real services** when testing integration
2. **Add rate limiting** for live service tests
3. **Handle failures gracefully** in integration tests
4. **Clean up after tests** to avoid side effects

## Troubleshooting

### Common Issues

#### Tests Failing in CI
- Check environment variables
- Verify network connectivity
- Check resource limits
- Review test isolation

#### Slow Test Execution
- Run unit tests separately
- Use parallel execution
- Optimize test data
- Review test dependencies

#### Coverage Issues
- Check coverage exclusions
- Verify test execution
- Review test quality
- Check coverage thresholds

#### Integration Test Failures
- Verify external service availability
- Check rate limiting
- Review test data
- Check network connectivity

### Getting Help
1. Check test logs for detailed error information
2. Review test documentation
3. Check CI/CD logs for environment issues
4. Verify test configuration
