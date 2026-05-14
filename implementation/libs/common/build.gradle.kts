/**
 * libs:common
 *
 * Shared utilities, domain primitives, and cross-cutting concerns.
 * This module has NO Spring dependency - pure Java library.
 */

plugins {
    id("pravah.java-conventions")
    `java-library`
}

description = "Pravah Common Library - Shared utilities and domain primitives"

dependencies {
    api(platform(libs.jackson.bom))
    api(libs.jackson.databind)
    api(libs.jackson.datatype.jsr310)

    api(libs.jakarta.validation.api)

    implementation(libs.guava)
    implementation(libs.commons.lang3)
}
