/**
 * services:pipeline-service
 *
 * Pipeline Service - Pipeline CRUD, versioning, and YAML validation.
 *
 * @see docs/architecture/high-level-architecture.md
 * @see docs/lld/02-database-erd.md - Pipeline Domain
 * @see docs/lld/03-state-machines.md - Pipeline States
 */

plugins {
    id("pravah.spring-boot-conventions")
    id("pravah.jpa-conventions")
    id("pravah.kafka-conventions")
}

description = "Pravah Pipeline Service - Pipeline definition management"

dependencies {
    implementation(project(":libs:common"))

    // Web
    implementation("org.springframework.boot:spring-boot-starter-web")

    // AOP (RLS aspect per ADR-013)
    implementation("org.springframework.boot:spring-boot-starter-aop")

    // Security (JWT per ADR-009)
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation(libs.jjwt.api)
    runtimeOnly(libs.jjwt.impl)
    runtimeOnly(libs.jjwt.jackson)

    // YAML parsing
    implementation("org.yaml:snakeyaml:2.2")

    // Test support
    testImplementation(project(":libs:test-support"))
}
