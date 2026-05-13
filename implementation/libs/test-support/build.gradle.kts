/**
 * libs:test-support
 *
 * Shared test utilities, fixtures, and Testcontainers configuration.
 * This module provides reusable testing infrastructure across services.
 */

plugins {
    id("pravah.java-conventions")
    `java-library`
}

description = "Pravah Test Support - Shared test utilities and fixtures"

dependencies {
    api(project(":libs:common"))

    api(platform(libs.spring.boot.dependencies))
    api("org.springframework.boot:spring-boot-starter-test") {
        exclude(group = "org.junit.vintage", module = "junit-vintage-engine")
    }

    api(platform(libs.testcontainers.bom))
    api(libs.testcontainers.junit.jupiter)
    api(libs.testcontainers.postgresql)
    api(libs.testcontainers.kafka)

    api(libs.awaitility)
    api(libs.datafaker)
}
