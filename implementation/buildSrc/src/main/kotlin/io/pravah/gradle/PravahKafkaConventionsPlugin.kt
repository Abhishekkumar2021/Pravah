package io.pravah.gradle

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.dependencies

class PravahKafkaConventionsPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        project.plugins.apply("pravah.java-conventions")
        val c = project.versionCatalog()
        val deps = project.dependencies
        project.dependencies {
            add("implementation", deps.platform(c.library("spring-boot-dependencies")))
            add("implementation", "org.springframework.kafka:spring-kafka")
            add("testImplementation", "org.springframework.kafka:spring-kafka-test")
            add("testImplementation", deps.platform(c.library("testcontainers-bom")))
            add("testImplementation", c.library("testcontainers-junit-jupiter"))
            add("testImplementation", c.library("testcontainers-kafka"))
            add("testImplementation", c.library("awaitility"))
        }
    }
}
