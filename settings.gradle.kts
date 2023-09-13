// apply the micronaut plugin version from the props file
// see https://docs.gradle.org/5.6/userguide/plugins.html#sec:plugin_version_management
pluginManagement {

    plugins {
        val kotlinVersion: String by settings
        kotlin("jvm") version kotlinVersion
        kotlin("plugin.allopen") version kotlinVersion
        kotlin("kapt") version kotlinVersion

        val micronautGradlePluginVersion = "4.1.0"
        id("io.micronaut.minimal.library") version micronautGradlePluginVersion

        id("com.lovelysystems.gradle") version "1.11.3"
        id("io.gitlab.arturbosch.detekt") version "1.22.0"
        id("com.github.johnrengelman.shadow") version "8.1.1"
        id("org.jetbrains.kotlinx.kover") version "0.7.0-Alpha"
    }
}

plugins {
    id("io.micronaut.platform.catalog") version "4.1.0"
}

dependencyResolutionManagement {

    // Centralized repository definitions
    repositories {
        mavenCentral()
    }

    // Catalogs
    versionCatalogs {
        create("testLibs") {
            val kotestApiVersion = "5.6.2"

            library("kotest-framework-api", "io.kotest", "kotest-framework-api-jvm").version(kotestApiVersion)

            library(
                "kotest-extensions-testcontainers",
                "io.kotest.extensions",
                "kotest-extensions-testcontainers"
            ).version("1.3.4")

        }
    }
}

rootProject.name = "lovely-micronaut"
