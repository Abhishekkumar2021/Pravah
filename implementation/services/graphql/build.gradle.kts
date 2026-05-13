/**
 * services:graphql
 * 
 * GraphQL Service - Provides GraphQL API for UI and external consumers.
 * Federation of multiple domain services into a unified graph.
 *
 * @see docs/architecture/high-level-architecture.md
 * @see docs/adr/ADR-015-graphql-api.md
 */

plugins {
    id("pravah.spring-boot-conventions")
}

description = "Pravah GraphQL Service - Unified GraphQL API"

dependencies {
    implementation(project(":libs:common"))
    
    // Spring for GraphQL
    implementation("org.springframework.boot:spring-boot-starter-graphql")
    implementation("org.springframework.boot:spring-boot-starter-webflux")
    
    // Security
    implementation("org.springframework.boot:spring-boot-starter-security")
    
    // GraphQL extensions
    implementation("com.graphql-java:graphql-java-extended-scalars:21.0")
    
    // Test support
    testImplementation(project(":libs:test-support"))
    testImplementation("org.springframework.graphql:spring-graphql-test")
}
