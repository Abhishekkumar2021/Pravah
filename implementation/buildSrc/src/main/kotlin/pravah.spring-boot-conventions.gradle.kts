/**
 * Pravah Spring Boot Conventions Plugin
 * 
 * Applies to Spring Boot SERVICE modules.
 * Extends java-conventions with Spring Boot specifics.
 */

plugins {
    id("pravah.java-conventions")
    id("org.springframework.boot")
}

// Spring Boot specific configuration
springBoot {
    buildInfo()
}

tasks.named<org.springframework.boot.gradle.tasks.bundling.BootJar>("bootJar") {
    archiveClassifier.set("")
    launchScript()
}

// Disable plain jar (we only want the fat jar)
tasks.named<Jar>("jar") {
    enabled = false
}

dependencies {
    // Spring Boot fundamentals
    implementation("org.springframework.boot:spring-boot-starter")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    
    // Structured logging
    implementation("net.logstash.logback:logstash-logback-encoder:7.4")
    
    // Observability
    implementation("io.micrometer:micrometer-registry-prometheus")
    
    // Spring Boot Test
    testImplementation("org.springframework.boot:spring-boot-starter-test") {
        exclude(group = "org.junit.vintage", module = "junit-vintage-engine")
    }
}
