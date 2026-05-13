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

// Disable proto resource copying - we only need generated Java code, not proto files in resources
// This avoids duplicate issues from extractProto and src/main/proto
tasks.matching { it.name.contains("processProtoResources") || it.name.contains("processTestProtoResources") || it.name.contains("processIntegrationTestProtoResources") }.configureEach {
    enabled = false
}

tasks.withType<ProcessResources>().configureEach {
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    exclude("**/*.proto")
}

dependencies {
    // Common module for shared domain types
    api(project(":libs:common"))
}
