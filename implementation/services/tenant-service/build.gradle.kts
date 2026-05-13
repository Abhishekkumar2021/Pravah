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

    // Web
    implementation("org.springframework.boot:spring-boot-starter-web")

    // AOP (for RlsAspect per ADR-013)
    implementation("org.springframework.boot:spring-boot-starter-aop")

    // Security
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation(libs.jjwt.api)
    runtimeOnly(libs.jjwt.impl)
    runtimeOnly(libs.jjwt.jackson)

    // Structured logging (JSON format for production)
    implementation(libs.logstash.logback.encoder)

    // Test support
    testImplementation(project(":libs:test-support"))
}
