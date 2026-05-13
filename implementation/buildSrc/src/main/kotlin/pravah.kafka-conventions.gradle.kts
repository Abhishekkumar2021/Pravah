/**
 * Pravah Kafka Conventions Plugin
 * 
 * Applies to modules that produce or consume Kafka events.
 * Includes Spring Kafka and Testcontainers setup.
 */

plugins {
    id("pravah.java-conventions")
}

dependencies {
    // Spring Kafka
    implementation("org.springframework.kafka:spring-kafka:3.1.4")
    
    // Testing
    testImplementation("org.springframework.kafka:spring-kafka-test:3.1.4")
    testImplementation(platform("org.testcontainers:testcontainers-bom:1.19.7"))
    testImplementation("org.testcontainers:junit-jupiter")
    testImplementation("org.testcontainers:kafka")
    testImplementation("org.awaitility:awaitility:4.2.1")
}
