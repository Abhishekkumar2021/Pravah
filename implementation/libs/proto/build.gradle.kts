/**
 * libs:proto
 *
 * Protobuf definitions and generated gRPC stubs.
 * This module is the single source of truth for all gRPC service definitions.
 *
 * @see docs/architecture/api-contracts.md - gRPC Service Definitions
 */

plugins {
    id("pravah.grpc-conventions")
    `java-library`
}

description = "Pravah Proto - gRPC service definitions and generated stubs"

// Proto files location
sourceSets {
    main {
        proto {
            srcDir("src/main/proto")
        }
        resources {
            // Exclude proto files from resources since they're handled by protobuf plugin
            exclude("**/*.proto")
        }
    }
}

tasks.named<ProcessResources>("processResources") {
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}

dependencies {
    // Common module for shared domain types
    api(project(":libs:common"))
}
