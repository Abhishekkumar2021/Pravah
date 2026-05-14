/**
 * services:tenant-service
 *
 * Tenant Service - Multi-tenancy management, user authentication, RBAC.
 *
 * @see docs/architecture/high-level-architecture.md
 * @see docs/lld/02-database-erd.md - Tenant Domain
 */

plugins {
    id("pravah.spring-boot-conventions")
    id("pravah.jpa-conventions")
    id("pravah.kafka-conventions")
}

description = "Pravah Tenant Service - Multi-tenancy and user management"

dependencies {
    implementation(project(":libs:common"))
    implementation(project(":libs:spring-support"))

    // Web
    implementation("org.springframework.boot:spring-boot-starter-web")

    // Security (JWT per ADR-009)
    // - JwtTokenVerifier from :libs:spring-support for token verification
    // - JwtTokenIssuer locally for RS256 token issuance (holds private key)
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation(libs.nimbus.jose.jwt)

    // Structured logging (JSON format for production)
    implementation(libs.logstash.logback.encoder)

    // Test support
    testImplementation(project(":libs:test-support"))
}
