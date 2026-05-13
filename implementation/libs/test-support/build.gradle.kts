/**
 * libs:test-support
 * 
 * Shared test utilities, fixtures, and Testcontainers configuration.
 * This module provides reusable testing infrastructure across services.
 */

plugins {
    id("pravah.java-conventions")
    `java-library`
}

description = "Pravah Test Support - Shared test utilities and fixtures"

dependencies {
    // Common module
    api(project(":libs:common"))
    
    // Spring Boot Test
    api("org.springframework.boot:spring-boot-starter-test:3.2.5") {
        exclude(group = "org.junit.vintage", module = "junit-vintage-engine")
    }
    
    // Testcontainers
    api(platform("org.testcontainers:testcontainers-bom:1.19.7"))
    api("org.testcontainers:junit-jupiter")
    api("org.testcontainers:postgresql")
    api("org.testcontainers:kafka")
    
    // Awaitility for async testing
    api("org.awaitility:awaitility:4.2.1")
    
    // Faker for test data generation
    api("net.datafaker:datafaker:2.1.0")
}
