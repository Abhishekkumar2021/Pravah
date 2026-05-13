/**
 * libs:common
 *
 * Shared utilities, domain primitives, and cross-cutting concerns.
 * This module has NO Spring dependency - pure Java library.
 */

plugins {
    id("pravah.java-conventions")
    `java-library`
}

description = "Pravah Common Library - Shared utilities and domain primitives"

dependencies {
    // Jackson for JSON serialization
    api(platform("com.fasterxml.jackson:jackson-bom:2.17.0"))
    api("com.fasterxml.jackson.core:jackson-databind")
    api("com.fasterxml.jackson.datatype:jackson-datatype-jsr310")

    // Validation API
    api("jakarta.validation:jakarta.validation-api:3.0.2")

    // Guava for collections and utilities
    implementation("com.google.guava:guava:33.6.0-jre")

    // Commons Lang for string utilities
    implementation("org.apache.commons:commons-lang3:3.14.0")
}
