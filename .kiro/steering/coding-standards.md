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
- **Commit after each task completion** - Always commit changes to version control after successfully completing each task from the implementation plan
- This ensures incremental progress is saved and provides clear checkpoints for rollback if needed