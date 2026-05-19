/**
 * services:execution-service
 *
 * Execution Service - Pipeline run orchestration and job management.
 *
 * @see docs/architecture/high-level-architecture.md
 * @see docs/lld/02-database-erd.md - Execution Domain
 * @see docs/lld/03-state-machines.md - Run and Job States
 */

plugins {
    id("pravah.spring-boot-conventions")
    id("pravah.jpa-conventions")
    id("pravah.kafka-conventions")
    id("pravah.grpc-conventions")
}

description = "Pravah Execution Service - Pipeline run orchestration"

dependencies {
    implementation(project(":libs:common"))
    implementation(project(":libs:proto"))
    implementation(project(":libs:spring-support"))

    // Web
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-websocket")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-actuator")

    // API Documentation (US-11.12)
    implementation(libs.springdoc.openapi.starter.webmvc.ui)

    // Redis pub/sub for multi-instance WebSocket fan-out (US-12.10)
    implementation("org.springframework.boot:spring-boot-starter-data-redis")

    // Security (JWT per ADR-009) — JwtTokenVerifier + ApiTenantJwtFilter from :libs:spring-support
    implementation("org.springframework.boot:spring-boot-starter-security")

    // Resilience (circuit breaker + retry for inter-service calls per ADR-012, LLD-01)
    implementation(libs.resilience4j.spring.boot3)
    implementation("org.springframework.boot:spring-boot-starter-aop")

    // Test support
    testImplementation(project(":libs:test-support"))
}
