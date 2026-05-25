/**
 * services:metadata-service
 *
 * Metadata Service - Artifact storage and run analytics.
 *
 * @see docs/architecture/high-level-architecture.md
 * @see docs/adr/ADR-009-duckdb-analytics.md
 */

plugins {
    id("pravah.spring-boot-conventions")
    id("pravah.kafka-conventions")
}

description = "Pravah Metadata Service - Artifacts and analytics"

dependencies {
    implementation(project(":libs:common"))
    implementation(project(":libs:spring-support"))

    // Web + JWT (ADR-009)
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-security")

    // DuckDB for analytics
    implementation("org.duckdb:duckdb_jdbc:0.10.1")

    // Object storage (MinIO/S3 compatible)
    implementation("software.amazon.awssdk:s3:2.25.21")
    implementation("software.amazon.awssdk:sts:2.25.21")

    // Test support
    testImplementation(project(":libs:test-support"))
}
