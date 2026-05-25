/**
 * services:runner-service
 *
 * Runner Service - Runner management and job assignment.
 *
 * @see docs/architecture/high-level-architecture.md
 * @see docs/lld/02-database-erd.md - Runner Domain
 * @see docs/lld/03-state-machines.md - Runner States
 */

plugins {
    id("pravah.spring-boot-conventions")
    id("pravah.jpa-conventions")
    id("pravah.kafka-conventions")
    id("pravah.grpc-conventions")
}

description = "Pravah Runner Service - Runner management and job dispatch"

dependencies {
    implementation(project(":libs:common"))
    implementation(project(":libs:proto"))
    implementation(project(":libs:spring-support"))

    // Web
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-security")

    // Redis for distributed locking
    implementation("org.springframework.boot:spring-boot-starter-data-redis")

    // OpenAPI documentation
    implementation(libs.springdoc.openapi.starter.webmvc.ui)

    // Test support
    testImplementation(project(":libs:test-support"))
}
