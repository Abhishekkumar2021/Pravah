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

    // Web
    implementation("org.springframework.boot:spring-boot-starter-web")

    // DuckDB for analytics
    implementation("org.duckdb:duckdb_jdbc:1.5.2.1")

    // Object storage (MinIO/S3 compatible)
    implementation("software.amazon.awssdk:s3:2.25.21")
    implementation("software.amazon.awssdk:sts:2.25.21")

    // Test support
    testImplementation(project(":libs:test-support"))
}
