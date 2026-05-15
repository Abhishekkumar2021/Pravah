/**
 * services:connect-service
 *
 * Connect Service - External integrations (GitHub, GitLab, etc.).
 *
 * @see docs/architecture/high-level-architecture.md
 * @see docs/lld/02-database-erd.md - Connect Domain
 */

plugins {
    id("pravah.spring-boot-conventions")
    id("pravah.jpa-conventions")
    id("pravah.kafka-conventions")
}

description = "Pravah Connect Service - External integrations"

dependencies {
    implementation(project(":libs:common"))

    // Web
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-webflux")

    // OAuth2 client for integrations
    implementation("org.springframework.boot:spring-boot-starter-oauth2-client")

    // Test support
    testImplementation(project(":libs:test-support"))
}
