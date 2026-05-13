/**
 * runner
 * 
 * Standalone Runner Binary - Deployed in customer infrastructure.
 * Connects to Runner Service via gRPC and executes jobs locally.
 *
 * @see docs/architecture/high-level-architecture.md
 * @see docs/lld/03-state-machines.md - Runner States
 */

plugins {
    id("pravah.java-conventions")
    id("pravah.grpc-conventions")
    application
}

description = "Pravah Runner - Standalone job execution agent"

application {
    mainClass.set("io.pravah.runner.RunnerMain")
}

dependencies {
    implementation(project(":libs:common"))
    implementation(project(":libs:proto"))
    
    // CLI argument parsing
    implementation("info.picocli:picocli:4.7.5")
    annotationProcessor("info.picocli:picocli-codegen:4.7.5")
    
    // YAML configuration
    implementation("org.yaml:snakeyaml:2.2")
    
    // HTTP client for health checks
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    
    // Logging
    implementation("ch.qos.logback:logback-classic:1.4.14")
    implementation("net.logstash.logback:logstash-logback-encoder:7.4")
    
    // Docker client for container execution
    implementation("com.github.docker-java:docker-java-core:3.3.6")
    implementation("com.github.docker-java:docker-java-transport-httpclient5:3.3.6")
    
    // Test support
    testImplementation(project(":libs:test-support"))
}

tasks.named<Jar>("jar") {
    manifest {
        attributes(
            "Main-Class" to "io.pravah.runner.RunnerMain",
            "Implementation-Title" to "Pravah Runner",
            "Implementation-Version" to project.version
        )
    }
}

// Create fat JAR for distribution
tasks.register<Jar>("fatJar") {
    archiveClassifier.set("all")
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    
    manifest {
        attributes(
            "Main-Class" to "io.pravah.runner.RunnerMain",
            "Implementation-Title" to "Pravah Runner",
            "Implementation-Version" to project.version
        )
    }
    
    from(sourceSets.main.get().output)
    
    dependsOn(configurations.runtimeClasspath)
    from({
        configurations.runtimeClasspath.get()
            .filter { it.name.endsWith("jar") }
            .map { zipTree(it) }
    })
}

tasks.named("build") {
    dependsOn("fatJar")
}
