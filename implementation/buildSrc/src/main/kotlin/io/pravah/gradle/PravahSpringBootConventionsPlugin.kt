package io.pravah.gradle

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.bundling.Jar
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.named
import org.springframework.boot.gradle.dsl.SpringBootExtension
import org.springframework.boot.gradle.tasks.bundling.BootJar

class PravahSpringBootConventionsPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        val catalog = project.versionCatalog()
        project.extensions.extraProperties.set(
            "testcontainers.version",
            catalog.version("testcontainers"),
        )

        project.plugins.apply("pravah.java-conventions")
        project.plugins.apply("org.springframework.boot")

        project.extensions.getByType(SpringBootExtension::class.java).buildInfo()

        project.tasks.named<BootJar>("bootJar").configure {
            archiveClassifier.set("")
            launchScript()
        }

        project.tasks.named<Jar>("jar").configure { isEnabled = false }

        project.dependencies {
            add("implementation", "org.springframework.boot:spring-boot-starter")
            add("implementation", "org.springframework.boot:spring-boot-starter-validation")
            add("implementation", "org.springframework.boot:spring-boot-starter-actuator")
            add("implementation", catalog.library("logstash-logback-encoder"))
            add("implementation", "io.micrometer:micrometer-registry-prometheus")
            add("testImplementation", "org.springframework.boot:spring-boot-starter-test")
        }
    }
}
