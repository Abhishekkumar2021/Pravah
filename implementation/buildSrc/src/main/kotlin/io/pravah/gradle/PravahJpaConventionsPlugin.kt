package io.pravah.gradle

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.dependencies

class PravahJpaConventionsPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        project.plugins.apply("pravah.java-conventions")
        val c = project.versionCatalog()
        project.dependencies {
            add("implementation", "org.springframework.boot:spring-boot-starter-data-jpa")
            add("runtimeOnly", "org.postgresql:postgresql")
            add("implementation", "org.flywaydb:flyway-core")
            add("implementation", "org.flywaydb:flyway-database-postgresql")
            add("testImplementation", c.library("testcontainers-junit-jupiter"))
            add("testImplementation", c.library("testcontainers-postgresql"))
        }
    }
}
