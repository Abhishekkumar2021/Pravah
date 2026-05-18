/**
 * services:notification-service
 *
 * Notification Service - Multi-channel notifications.
 *
 * @see docs/architecture/high-level-architecture.md
 * @see docs/lld/02-database-erd.md - Notification Domain
 */

plugins {
    id("pravah.spring-boot-conventions")
    id("pravah.jpa-conventions")
    id("pravah.kafka-conventions")
}

description = "Pravah Notification Service - Multi-channel notifications"

dependencies {
    implementation(project(":libs:common"))
    implementation(project(":libs:spring-support"))

    // Web
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-validation")

    // Security
    implementation("org.springframework.boot:spring-boot-starter-security")

    // Email
    implementation("org.springframework.boot:spring-boot-starter-mail")

    // Template engine for notifications
    implementation("org.springframework.boot:spring-boot-starter-thymeleaf")

    // Structured logging
    implementation(libs.logstash.logback.encoder)

    // Test support
    testImplementation(project(":libs:test-support"))
}
