/**
 * services:execution-service
 * 
 * Execution Service - Pipeline run orchestration and job management.
 *
 * @see docs/architecture/high-level-architecture.md
 * @see docs/lld/02-database-erd.md - Execution Domain
 * @see docs/lld/03-state-machines.md - Run and Job States
 */

plugins {
    id("pravah.spring-boot-conventions")
    id("pravah.jpa-conventions")
    id("pravah.kafka-conventions")
    id("pravah.grpc-conventions")
}

description = "Pravah Execution Service - Pipeline run orchestration"

dependencies {
    implementation(project(":libs:common"))
    implementation(project(":libs:proto"))
    
    // Web
    implementation("org.springframework.boot:spring-boot-starter-web")
    
    // Test support
    testImplementation(project(":libs:test-support"))
}
