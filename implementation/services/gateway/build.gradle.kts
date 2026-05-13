/**
 * services:gateway
 *
 * API Gateway Service - Entry point for all external API requests.
 * Handles authentication, rate limiting, and request routing.
 *
 * @see docs/architecture/high-level-architecture.md
 * @see docs/adr/ADR-001-api-gateway.md
 */

plugins {
    id("pravah.spring-boot-conventions")
}

description = "Pravah API Gateway - External API entry point"

dependencies {
    implementation(project(":libs:common"))

    // Spring Cloud Gateway
    implementation("org.springframework.cloud:spring-cloud-starter-gateway:4.3.4")

    // Security
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-oauth2-resource-server")

    // Redis for rate limiting and session
    implementation("org.springframework.boot:spring-boot-starter-data-redis-reactive")

    // Test support
    testImplementation(project(":libs:test-support"))
}

dependencyManagement {
    imports {
        mavenBom("org.springframework.cloud:spring-cloud-dependencies:2023.0.1")
    }
}
