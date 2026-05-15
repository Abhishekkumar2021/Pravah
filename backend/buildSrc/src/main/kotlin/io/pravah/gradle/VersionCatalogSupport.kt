package io.pravah.gradle

import org.gradle.api.Project
import org.gradle.api.artifacts.MinimalExternalModuleDependency
import org.gradle.api.artifacts.VersionCatalog
import org.gradle.api.artifacts.VersionCatalogsExtension

internal fun Project.versionCatalog(name: String = "libs"): VersionCatalog =
    extensions.getByType(VersionCatalogsExtension::class.java).named(name)

/** Resolves a version-catalog library alias to a concrete module dependency. */
internal fun VersionCatalog.library(alias: String): MinimalExternalModuleDependency =
    findLibrary(alias).orElseThrow { IllegalArgumentException("Unknown catalog library '$alias'") }.get()

internal fun VersionCatalog.version(alias: String): String =
    findVersion(alias).orElseThrow { IllegalArgumentException("Unknown catalog version '$alias'") }.requiredVersion
