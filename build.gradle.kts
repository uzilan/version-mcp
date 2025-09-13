plugins {
    kotlin("jvm") version "2.0.0"
    kotlin("plugin.serialization") version "2.0.0"
    application
    id("org.jlleitschuh.gradle.ktlint") version "12.1.0"
}

group = "com.mavenversion.mcp"
version = "1.0.0"

repositories {
    mavenCentral()
}

dependencies {
    // Note: Kotlin MCP SDK will be added when available
    // For now, we'll implement MCP protocol manually

    // Playwright for web automation
    implementation("com.microsoft.playwright:playwright:1.39.0")

    // Kotlinx serialization
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.5.1")

    // XML processing
    implementation("org.dom4j:dom4j:2.1.4")
    implementation("jaxen:jaxen:2.0.0")

    // HTML parsing
    implementation("org.jsoup:jsoup:1.16.1")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")

    // Note: HTTP client dependencies removed - now using stdio-based MCP communication

    // Logging
    implementation("ch.qos.logback:logback-classic:1.4.11")
    implementation("io.github.microutils:kotlin-logging-jvm:3.0.5")

    // Testing
    testImplementation(kotlin("test"))
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.0")
    testImplementation("org.assertj:assertj-core:3.24.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3")
    testImplementation("io.mockk:mockk:1.13.8")
}

application {
    mainClass.set("com.mavenversion.mcp.MainKt")
}

tasks.test {
    useJUnitPlatform()
}

kotlin {
    jvmToolchain(21)
}

ktlint {
    version.set("1.0.1")
    debug.set(false)
    verbose.set(true)
    android.set(false)
    outputToConsole.set(true)
    outputColorName.set("RED")
    ignoreFailures.set(false)
    enableExperimentalRules.set(true)

    filter {
        exclude("**/generated/**")
        include("**/kotlin/**")
    }
}

tasks.named("ktlintTestSourceSetCheck") {
    enabled = false
}
