# Coding Standards and Preferences

## Testing Framework
- **Use JUnit 5** instead of kotlin.test
- **Use AssertJ** for assertions instead of basic assertEquals/assertTrue
- Organize tests with `@Nested` classes and `@DisplayName` annotations
- Use descriptive test method names

## Logging
- **Use kotlin-logging with logback** instead of println statements
- Import: `import mu.KotlinLogging`
- Create logger: `private val log = KotlinLogging.logger {}`
- Use lazy evaluation: `log.info { "message" }`
- Logback configuration is already set up in `src/main/resources/logback.xml`

## Dependencies Already Configured
- JUnit 5: `org.junit.jupiter:junit-jupiter:5.10.0`
- AssertJ: `org.assertj:assertj-core:3.24.2`
- Kotlin Logging: `io.github.microutils:kotlin-logging-jvm:3.0.5`
- Logback: `ch.qos.logback:logback-classic:1.4.11`

## Example Patterns

### Test Structure
```kotlin
@Nested
@DisplayName("Feature Tests")
inner class FeatureTests {
    
    @Test
    @DisplayName("Should do something specific")
    fun testSomething() {
        assertThat(result).isEqualTo(expected)
        assertThat(list).hasSize(2)
        assertThat(value).isNull()
    }
}
```

### Logging Pattern
```kotlin
import mu.KotlinLogging

private val log = KotlinLogging.logger {}

class MyClass {
    fun doSomething() {
        log.info { "Starting operation" }
        log.debug { "Debug details: $details" }
        log.error { "Error occurred: ${exception.message}" }
    }
}
```

## Development Workflow
- **Run all tests before committing** - Always run `./gradlew test` to ensure all tests pass before committing changes
- **Run build before committing** - Always run `./gradlew assemble` to ensure code compiles successfully before committing changes
- **Commit after each task completion** - Always commit changes to version control after successfully completing each task from the implementation plan
- This ensures incremental progress is saved and provides clear checkpoints for rollback if needed
- If ktlint issues prevent build completion, use `./gradlew test -x ktlintCheck -x ktlintMainSourceSetCheck -x ktlintTestSourceSetCheck -x ktlintKotlinScriptCheck -x runKtlintCheckOverMainSourceSet -x runKtlintCheckOverTestSourceSet` to run tests without formatting checks

## Build and Quality Checks
- **Primary build command**: `./gradlew build` - Runs compilation, tests, and code quality checks
- **Test execution**: `./gradlew test` - Runs all unit and integration tests
- **Compilation only**: `./gradlew assemble` - Verifies code compiles without running tests
- **Code formatting**: `./gradlew ktlintFormat` - Auto-formats code according to Kotlin style guide
- **Skip ktlint when needed**: Add `-x ktlintCheck -x ktlintMainSourceSetCheck -x ktlintTestSourceSetCheck -x ktlintKotlinScriptCheck -x runKtlintCheckOverMainSourceSet -x runKtlintCheckOverTestSourceSet` to bypass formatting checks during development

## Testing Requirements
- **Write tests for all new functionality** - Every new class, method, or feature must have corresponding unit tests
- **Maintain test coverage** - Aim for comprehensive test coverage of business logic and edge cases
- **Test naming convention**: Use descriptive test method names and `@DisplayName` annotations
- **Test organization**: Group related tests using `@Nested` inner classes
- **Test isolation**: Each test should be independent and not rely on other tests
- **Mock external dependencies**: Use MockK for mocking external services and dependencies