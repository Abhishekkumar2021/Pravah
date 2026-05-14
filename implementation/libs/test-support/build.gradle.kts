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

    // Propagate Spring Boot + Testcontainers BOMs to consumers (e.g. :runner) so api() deps keep versions.
    // Spring Boot BOM pins Testcontainers 1.20.x; enforced Testcontainers BOM aligns with libs.versions.toml
    // (Docker Engine 29+ API). Spring Boot services also set testcontainers.version via conventions.
    api(platform(libs.spring.boot.dependencies))
    api(enforcedPlatform(libs.testcontainers.bom))

    api("org.springframework.boot:spring-boot-starter-test") {
        exclude(group = "org.junit.vintage", module = "junit-vintage-engine")
    }

    api(libs.testcontainers.junit.jupiter)
    api(libs.testcontainers.postgresql)
    api(libs.testcontainers.kafka)

    api(libs.awaitility)
    api(libs.datafaker)
}
