package io.pravah.gradle

import com.google.protobuf.gradle.ProtobufExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.file.DuplicatesStrategy
import org.gradle.api.tasks.AbstractCopyTask
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.api.tasks.Sync
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.named
import org.gradle.kotlin.dsl.withType

class PravahGrpcConventionsPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        project.plugins.apply("pravah.java-conventions")
        project.plugins.apply("com.google.protobuf")

        val c = project.versionCatalog()
        val protobufVer = c.version("protobuf")
        val grpcVer = c.version("grpc")

        project.extensions.configure(ProtobufExtension::class.java) {
            protoc { artifact = "com.google.protobuf:protoc:$protobufVer" }
            plugins {
                create("grpc") { artifact = "io.grpc:protoc-gen-grpc-java:$grpcVer" }
            }
            generateProtoTasks {
                all().forEach { task -> task.plugins { create("grpc") } }
            }
        }

        project.extensions.configure(SourceSetContainer::class.java) {
            named("main") {
                java.srcDirs(
                    "build/generated/source/proto/main/java",
                    "build/generated/source/proto/main/grpc",
                )
                resources.exclude("**/*.proto")
            }
        }

        project.tasks.withType(AbstractCopyTask::class.java).configureEach {
            duplicatesStrategy = DuplicatesStrategy.EXCLUDE
        }

        project.dependencies {
            add("implementation", c.library("grpc-netty-shaded"))
            add("implementation", c.library("grpc-protobuf"))
            add("implementation", c.library("grpc-stub"))
            add("implementation", c.library("grpc-services"))
            add("implementation", c.library("protobuf-java"))
            add("implementation", c.library("protobuf-java-util"))
            add("compileOnly", c.library("javax-annotation-api"))
            add("testImplementation", c.library("grpc-testing"))
        }
    }
}
