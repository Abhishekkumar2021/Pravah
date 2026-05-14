/**
 * Pravah Java Conventions Plugin
 *
 * Applies to ALL Java modules in Pravah.
 * Sets up Java 21, compiler options, testing, and common dependencies.
 *
 * NOTE: Dependency versions are hardcoded here because the Gradle version catalog
 * (gradle/libs.versions.toml) is not accessible from buildSrc. These versions MUST
 * be kept in sync with the corresponding versions in gradle/libs.versions.toml.
 * When updating versions, update both this file and libs.versions.toml.
 */

plugins {
    java
    id("io.spring.dependency-management")
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
    options.compilerArgs.addAll(
        listOf(
            "-Xlint:all", // Enable all warnings
            "-Xlint:-processing", // Suppress annotation processing warnings
            "-Werror", // Treat warnings as errors
            "-parameters", // Preserve parameter names for reflection
        ),
    )
}

tasks.withType<Test> {
    useJUnitPlatform()
    testLogging {
        events("passed", "skipped", "failed")
        showStandardStreams = false
        showExceptions = true
        showCauses = true
        showStackTraces = true
    }
    // Fail fast on first test failure
    failFast = false
}

// ============================================================================
// Integration Tests Configuration
// ============================================================================
sourceSets {
    create("integrationTest") {
        java.srcDir("src/integrationTest/java")
        resources.srcDir("src/integrationTest/resources")
        compileClasspath += sourceSets["main"].output + sourceSets["test"].output
        runtimeClasspath += sourceSets["main"].output + sourceSets["test"].output
    }
}

configurations["integrationTestImplementation"].extendsFrom(configurations["testImplementation"])
configurations["integrationTestRuntimeOnly"].extendsFrom(configurations["testRuntimeOnly"])

tasks.register<Test>("integrationTest") {
    description = "Runs integration tests"
    group = "verification"
    testClassesDirs = sourceSets["integrationTest"].output.classesDirs
    classpath = sourceSets["integrationTest"].runtimeClasspath
    shouldRunAfter(tasks.named("test"))

    useJUnitPlatform {
        includeTags("integration")
    }
}

// Common dependencies for ALL modules
dependencies {
    // Logging
    implementation("org.slf4j:slf4j-api:2.0.12")

    // Lombok (compile-only)
    compileOnly("org.projectlombok:lombok:1.18.32")
    annotationProcessor("org.projectlombok:lombok:1.18.32")
    testCompileOnly("org.projectlombok:lombok:1.18.32")
    testAnnotationProcessor("org.projectlombok:lombok:1.18.32")

    // Testing
    testImplementation(platform("org.junit:junit-bom:5.10.2"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("org.assertj:assertj-core:3.25.3")
    testImplementation("org.mockito:mockito-junit-jupiter:5.11.0")
}
