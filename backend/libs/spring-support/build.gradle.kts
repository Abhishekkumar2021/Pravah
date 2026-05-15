/**
 * libs:spring-support
 *
 * Shared Spring infrastructure: RLS aspect, tenant context, transaction ordering, and common JWT
 * security for tenant-scoped API services (ADR-009, ADR-013).
 */

plugins {
    id("pravah.java-conventions")
    `java-library`
}

description = "Pravah Spring Support - Multi-tenancy, RLS, and shared API security"

dependencies {
    api(project(":libs:common"))

    api(platform(libs.spring.boot.dependencies))

    // AOP for RlsAspect
    api("org.springframework.boot:spring-boot-starter-aop")

    // JPA for EntityManager in RlsAspect
    compileOnly("org.springframework.boot:spring-boot-starter-data-jpa")

    // Servlet API + security for ApiTenantJwtFilter (strict /api/** JWT enforcement)
    api("org.springframework.boot:spring-boot-starter-web")
    api("org.springframework.boot:spring-boot-starter-security")

    // JWT verification (ADR-009) — RS256 with JWKS support for verifying tokens
    api(libs.nimbus.jose.jwt)

    // Structured logging
    implementation(libs.logstash.logback.encoder)

    // Test
    testImplementation(project(":libs:test-support"))
    testImplementation("org.springframework.boot:spring-boot-starter-data-jpa")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.boot:spring-boot-starter-security")
}
