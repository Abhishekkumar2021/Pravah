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

    // Web
    implementation("org.springframework.boot:spring-boot-starter-web")

    // Email
    implementation("org.springframework.boot:spring-boot-starter-mail")

    // Template engine for notifications
    implementation("org.springframework.boot:spring-boot-starter-thymeleaf")

    // Test support
    testImplementation(project(":libs:test-support"))
}
