plugins {
    id("java")
    id("idea")
    id("com.diffplug.spotless") version "6.25.0"
    id("org.owasp.dependencycheck") version "9.0.10" apply false
}

group = "io.pravah"
version = "0.1.0-SNAPSHOT"

// ============================================================================
// Code Formatting (Spotless)
// ============================================================================
spotless {
    java {
        target("**/*.java")
        targetExclude("**/build/**", "**/generated/**")
        googleJavaFormat("1.19.2")
        removeUnusedImports()
        trimTrailingWhitespace()
        endWithNewline()
    }
    kotlinGradle {
        target("**/*.gradle.kts")
        targetExclude("**/build/**")
        ktlint("1.1.1").editorConfigOverride(
            mapOf(
                "ktlint_standard_value-argument-comment" to "disabled",
                "ktlint_standard_comment-wrapping" to "disabled",
                "ktlint_standard_no-wildcard-imports" to "disabled",
            ),
        )
    }
}

// ============================================================================
// Subproject Configuration
// ============================================================================
subprojects {
    group = rootProject.group
    version = rootProject.version

    apply(plugin = "org.owasp.dependencycheck")

    tasks.withType<JavaCompile> {
        options.encoding = "UTF-8"
    }

    // Configure dependency check for security scanning
    configure<org.owasp.dependencycheck.gradle.extension.DependencyCheckExtension> {
        failBuildOnCVSS = 9.0f // Only fail on critical vulnerabilities
        suppressionFile = "${rootProject.projectDir}/config/owasp-suppressions.xml"
        formats = listOf("HTML", "JSON")
    }
}

// ============================================================================
// IDE Configuration
// ============================================================================
idea {
    module {
        isDownloadJavadoc = true
        isDownloadSources = true
    }
}

// ============================================================================
// Aggregated Tasks
// ============================================================================
tasks.register("cleanAll") {
    description = "Clean all subprojects"
    group = "build"
    dependsOn(subprojects.map { it.tasks.named("clean") })
}

tasks.register("buildAll") {
    description = "Build all subprojects"
    group = "build"
    dependsOn(subprojects.map { it.tasks.named("build") })
}

tasks.register("testAll") {
    description = "Run tests in all subprojects"
    group = "verification"
    dependsOn(subprojects.map { it.tasks.named("test") })
}
