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

    implementation(platform(libs.spring.cloud.dependencies))
    implementation(libs.spring.cloud.starter.gateway)

    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-oauth2-resource-server")

    implementation("org.springframework.boot:spring-boot-starter-data-redis-reactive")

    testImplementation(project(":libs:test-support"))
}
