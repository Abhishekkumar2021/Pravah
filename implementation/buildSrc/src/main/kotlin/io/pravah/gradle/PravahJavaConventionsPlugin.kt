package io.pravah.gradle

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.api.tasks.testing.Test
import org.gradle.api.tasks.testing.logging.TestLogEvent
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.getByType
import org.gradle.kotlin.dsl.named
import org.gradle.kotlin.dsl.register
import org.gradle.kotlin.dsl.withType
import org.gradle.testing.jacoco.plugins.JacocoPlugin
import org.gradle.testing.jacoco.plugins.JacocoPluginExtension
import org.gradle.testing.jacoco.tasks.JacocoReport

class PravahJavaConventionsPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        project.plugins.apply(JavaPlugin::class.java)
        project.plugins.apply("io.spring.dependency-management")
        project.plugins.apply(JacocoPlugin::class.java)

        project.extensions.getByType(JavaPluginExtension::class.java).apply {
            toolchain { languageVersion.set(JavaLanguageVersion.of(21)) }
        }

        project.tasks.withType(JavaCompile::class.java).configureEach {
            options.encoding = "UTF-8"
            options.compilerArgs.addAll(
                listOf(
                    "-Xlint:all",
                    "-Xlint:-processing",
                    "-Werror",
                    "-parameters",
                ),
            )
        }

        project.tasks.withType(Test::class.java).configureEach {
            useJUnitPlatform()
            testLogging {
                events(
                    TestLogEvent.PASSED,
                    TestLogEvent.SKIPPED,
                    TestLogEvent.FAILED,
                )
                showStandardStreams = false
                showExceptions = true
                showCauses = true
                showStackTraces = true
            }
            failFast = false
            finalizedBy(project.tasks.named("jacocoTestReport"))
        }

        project.extensions.configure<JacocoPluginExtension> {
            toolVersion = "0.8.11"
        }

        project.tasks.withType(JacocoReport::class.java).configureEach {
            dependsOn(project.tasks.withType(Test::class.java))
            reports {
                xml.required.set(true)
                html.required.set(true)
                csv.required.set(false)
            }
        }

        val sourceSets = project.extensions.getByType(SourceSetContainer::class.java)
        val main = sourceSets.getByName(SourceSet.MAIN_SOURCE_SET_NAME)
        val test = sourceSets.getByName(SourceSet.TEST_SOURCE_SET_NAME)

        val integrationTest =
            sourceSets.create("integrationTest") {
                java.srcDir("src/integrationTest/java")
                resources.srcDir("src/integrationTest/resources")
                compileClasspath += main.output + test.output
                runtimeClasspath += main.output + test.output
            }

        project.configurations.named("integrationTestImplementation").configure {
            extendsFrom(project.configurations.getByName("testImplementation"))
        }
        project.configurations.named("integrationTestRuntimeOnly").configure {
            extendsFrom(project.configurations.getByName("testRuntimeOnly"))
        }

        project.tasks.register<Test>("integrationTest") {
            description = "Runs integration tests"
            group = "verification"
            testClassesDirs = integrationTest.output.classesDirs
            classpath = integrationTest.runtimeClasspath
            shouldRunAfter(project.tasks.named("test"))
            useJUnitPlatform { includeTags("integration") }
        }

        val c = project.versionCatalog()
        project.dependencies {
            add("implementation", c.library("slf4j-api"))
            add("compileOnly", c.library("lombok"))
            add("annotationProcessor", c.library("lombok"))
            add("testCompileOnly", c.library("lombok"))
            add("testAnnotationProcessor", c.library("lombok"))
            add(
                "testImplementation",
                project.dependencies.platform(c.library("junit-bom")),
            )
            add("testImplementation", c.library("junit-jupiter"))
            add("testImplementation", c.library("assertj-core"))
            add("testImplementation", c.library("mockito-junit-jupiter"))
        }
    }
}
