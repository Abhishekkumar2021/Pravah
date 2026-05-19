/**
 * services:scheduler-service
 *
 * Scheduler Service - Cron-based pipeline scheduling.
 *
 * @see docs/lld/02-database-erd.md - Scheduler Domain
 * @see docs/lld/04-sequence-diagrams.md - Scheduled Trigger
 */

plugins {
    id("pravah.spring-boot-conventions")
    id("pravah.jpa-conventions")
    id("pravah.kafka-conventions")
}

description = "Pravah Scheduler Service - Pipeline scheduling"

dependencies {
    implementation(project(":libs:common"))
    implementation(project(":libs:spring-support"))

    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-data-redis")

    implementation("com.cronutils:cron-utils:9.2.1")

    // Resilience (circuit breaker + retry for inter-service calls per ADR-012, LLD-01)
    implementation(libs.resilience4j.spring.boot3)
    implementation("org.springframework.boot:spring-boot-starter-aop")

    testImplementation(project(":libs:test-support"))
}
