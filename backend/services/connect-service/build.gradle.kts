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

    // Web
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-webflux")

    // OAuth2 client for integrations
    implementation("org.springframework.boot:spring-boot-starter-oauth2-client")

    // OpenAPI/Swagger documentation
    implementation(libs.springdoc.openapi.starter.webmvc.ui)

    // Database drivers
    implementation("org.postgresql:postgresql")
    implementation(libs.mysql.connector)
    implementation(libs.mssql.jdbc)
    implementation(libs.oracle.jdbc)
    implementation(libs.mongodb.driver)

    // AWS S3 SDK for S3/MinIO connector
    implementation(libs.aws.s3)

    // Apache Commons (FTP)
    implementation(libs.commons.net)
    implementation(libs.commons.io)

    // JSch for SFTP
    implementation(libs.jsch)

    // Test support
    testImplementation(project(":libs:test-support"))
}
