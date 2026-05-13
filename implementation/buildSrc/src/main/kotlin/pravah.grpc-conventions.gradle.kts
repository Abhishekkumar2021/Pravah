/**
 * Pravah gRPC Conventions Plugin
 *
 * Applies to modules that use gRPC for inter-service communication.
 * Configures Protobuf compilation and gRPC code generation.
 */

import com.google.protobuf.gradle.*

plugins {
    id("pravah.java-conventions")
    id("com.google.protobuf")
}

// Protobuf configuration
protobuf {
    protoc {
        artifact = "com.google.protobuf:protoc:3.25.3"
    }
    plugins {
        create("grpc") {
            artifact = "io.grpc:protoc-gen-grpc-java:1.62.2"
        }
    }
    generateProtoTasks {
        all().forEach { task ->
            task.plugins {
                create("grpc")
            }
        }
    }
}

// Add generated sources to source sets
sourceSets {
    main {
        java {
            srcDirs(
                "build/generated/source/proto/main/java",
                "build/generated/source/proto/main/grpc",
            )
        }
        resources {
            // Exclude proto files from resources - they're handled by protobuf plugin
            exclude("**/*.proto")
        }
    }
}

// Handle duplicate proto resources for ALL Sync-based tasks (including ProtoSyncTask)
tasks.withType<Sync>().configureEach {
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}

tasks.withType<Copy>().configureEach {
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}

tasks.withType<ProcessResources>().configureEach {
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}

dependencies {
    // gRPC dependencies
    implementation("io.grpc:grpc-netty-shaded:1.62.2")
    implementation("io.grpc:grpc-protobuf:1.62.2")
    implementation("io.grpc:grpc-stub:1.62.2")
    implementation("io.grpc:grpc-services:1.62.2")

    // Protobuf
    implementation("com.google.protobuf:protobuf-java:3.25.3")
    implementation("com.google.protobuf:protobuf-java-util:3.25.3")

    // Required for gRPC generated code
    compileOnly("javax.annotation:javax.annotation-api:1.3.2")

    // Testing
    testImplementation("io.grpc:grpc-testing:1.62.2")
}
