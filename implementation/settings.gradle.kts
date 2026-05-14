rootProject.name = "pravah"

// Plugin management
pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

// Dependency resolution
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        mavenCentral()
    }
}

// Enable version catalog
enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

// ============================================================================
// Shared Libraries
// ============================================================================
include(":libs:common")
include(":libs:proto")
include(":libs:spring-support")
include(":libs:test-support")

// ============================================================================
// Services (alphabetical)
// ============================================================================
include(":services:agent-service")
include(":services:connect-service")
include(":services:execution-service")
include(":services:gateway")
include(":services:graphql")
include(":services:metadata-service")
include(":services:notification-service")
include(":services:pipeline-service")
include(":services:runner-service")
include(":services:scheduler-service")
include(":services:tenant-service")

// ============================================================================
// Runner (standalone binary for customer infrastructure)
// ============================================================================
include(":runner")
