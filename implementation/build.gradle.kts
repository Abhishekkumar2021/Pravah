plugins {
    id("java")
    id("idea")
}

group = "io.pravah"
version = "0.1.0-SNAPSHOT"

// ============================================================================
// Subproject Configuration
// ============================================================================
subprojects {
    group = rootProject.group
    version = rootProject.version

    tasks.withType<JavaCompile> {
        options.encoding = "UTF-8"
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
