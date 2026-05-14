/**
 * libs:spring-support
 *
 * Shared Spring infrastructure: RLS aspect, tenant context, transaction ordering.
 * Services with Spring + JPA depend on this module for multi-tenancy support.
 */

plugins {
    id("pravah.java-conventions")
    `java-library`
}

description = "Pravah Spring Support - Shared Spring infrastructure for multi-tenancy"

dependencies {
    api(project(":libs:common"))

    api(platform(libs.spring.boot.dependencies))

    // AOP for RlsAspect
    api("org.springframework.boot:spring-boot-starter-aop")

    // JPA for EntityManager in RlsAspect
    compileOnly("org.springframework.boot:spring-boot-starter-data-jpa")

    // Structured logging
    implementation(libs.logstash.logback.encoder)

    // Test
    testImplementation(project(":libs:test-support"))
    testImplementation("org.springframework.boot:spring-boot-starter-data-jpa")
}
