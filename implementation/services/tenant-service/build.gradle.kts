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

    // Security
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("io.jsonwebtoken:jjwt-api:0.12.5")
    runtimeOnly("io.jsonwebtoken:jjwt-impl:0.12.5")
    runtimeOnly("io.jsonwebtoken:jjwt-jackson:0.12.5")

    // Test support
    testImplementation(project(":libs:test-support"))
}
