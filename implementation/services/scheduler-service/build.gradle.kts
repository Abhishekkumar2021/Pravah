/**
 * services:scheduler-service
 *
 * Scheduler Service - Cron-based pipeline scheduling.
 *
 * @see docs/architecture/high-level-architecture.md
 * @see docs/lld/02-database-erd.md - Scheduler Domain
 */

plugins {
    id("pravah.spring-boot-conventions")
    id("pravah.jpa-conventions")
    id("pravah.grpc-conventions")
}

description = "Pravah Scheduler Service - Pipeline scheduling"

dependencies {
    implementation(project(":libs:common"))
    implementation(project(":libs:proto"))

    // Web
    implementation("org.springframework.boot:spring-boot-starter-web")

    // Quartz for scheduling
    implementation("org.springframework.boot:spring-boot-starter-quartz")

    // Cron expression parsing
    implementation("com.cronutils:cron-utils:9.2.1")

    // Test support
    testImplementation(project(":libs:test-support"))
}
