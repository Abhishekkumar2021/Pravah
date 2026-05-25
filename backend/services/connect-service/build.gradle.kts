/**
 * services:connect-service
 *
 * Connect Service - Data connectors and integrations.
 *
 * @see docs/architecture/high-level-architecture.md
 * @see docs/adr/ADR-029-connect-service-kafka-connect.md
 */

plugins {
    id("pravah.spring-boot-conventions")
    id("pravah.jpa-conventions")
    id("pravah.kafka-conventions")
}

description = "Pravah Connect Service - Data connectors and integrations"

dependencies {
    implementation(project(":libs:common"))
    implementation(project(":libs:spring-support"))

    // Web + security (JWT per ADR-009)
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-webflux")
    implementation("org.springframework.boot:spring-boot-starter-security")

    // OAuth2 client for integrations
    implementation("org.springframework.boot:spring-boot-starter-oauth2-client")

    // OpenAPI/Swagger documentation
    implementation(libs.springdoc.openapi.starter.webmvc.ui)

    // Database drivers
    implementation("org.postgresql:postgresql")
    implementation(libs.mysql.connector)
    implementation(libs.mssql.jdbc)
    implementation(libs.oracle.jdbc)
    // Align with Spring Boot BOM (avoid bson/driver-core version skew)
    implementation("org.mongodb:mongodb-driver-sync")
    implementation(libs.snowflake.jdbc)

    // AWS S3 SDK for S3/MinIO connector
    implementation(libs.aws.s3)

    // Apache Commons (FTP)
    implementation(libs.commons.net)
    implementation(libs.commons.io)

    // JSch for SFTP
    implementation(libs.jsch)

    // Streaming - RabbitMQ
    implementation(libs.amqp.client)

    // Test support
    testImplementation(project(":libs:test-support"))
}
