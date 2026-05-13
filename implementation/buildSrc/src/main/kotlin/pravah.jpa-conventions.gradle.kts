/**
 * Pravah JPA Conventions Plugin
 * 
 * Applies to modules that use JPA/Hibernate for database access.
 * Includes Flyway migrations and PostgreSQL configuration.
 */

plugins {
    id("pravah.java-conventions")
}

dependencies {
    // Spring Data JPA
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    
    // PostgreSQL Driver
    runtimeOnly("org.postgresql:postgresql:42.7.3")
    
    // HikariCP (included in spring-boot-starter-data-jpa, but explicit for clarity)
    implementation("com.zaxxer:HikariCP:5.1.0")
    
    // Flyway Migrations
    implementation("org.flywaydb:flyway-core:10.10.0")
    implementation("org.flywaydb:flyway-database-postgresql:10.10.0")
    
    // Testcontainers for integration tests
    testImplementation(platform("org.testcontainers:testcontainers-bom:1.19.7"))
    testImplementation("org.testcontainers:junit-jupiter")
    testImplementation("org.testcontainers:postgresql")
}
