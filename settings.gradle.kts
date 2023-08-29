// apply the micronaut plugin version from the props file
// see https://docs.gradle.org/5.6/userguide/plugins.html#sec:plugin_version_management
pluginManagement {

    plugins {
        val kotlinVersion: String by settings
        kotlin("jvm") version kotlinVersion
        kotlin("plugin.allopen") version kotlinVersion
        kotlin("kapt") version kotlinVersion

        val micronautGradlePluginVersion = "4.0.2"
        id("io.micronaut.minimal.library") version micronautGradlePluginVersion

        id("com.lovelysystems.gradle") version "1.11.3"
        id("io.gitlab.arturbosch.detekt") version "1.22.0"
        id("com.github.johnrengelman.shadow") version "8.1.1"
        id("org.jetbrains.kotlinx.kover") version "0.7.0-Alpha"
    }
}

dependencyResolutionManagement {

    // Centralized repository definitions
    repositories {
        mavenCentral()
    }

    // Catalogs
    versionCatalogs {
        create("libs") {

            // Kotlin & KotlinX
            val kotlinxGroup = "org.jetbrains.kotlinx"
            val coroutinesPrefix = "kotlinx-coroutines"

            val coroutinesVersion = "1.6.4"

            library("$coroutinesPrefix-core", kotlinxGroup, "$coroutinesPrefix-core").version(coroutinesVersion)
            library("$coroutinesPrefix-reactor", kotlinxGroup, "$coroutinesPrefix-reactor").version(coroutinesVersion)
            val kotlinVersion: String by settings
            library("kotlin-reflect", "org.jetbrains.kotlin", "kotlin-reflect").version(kotlinVersion)
        }

        create("micronautLibs") {
            version("micronaut", "4.0.5")
            library("management", "io.micronaut", "micronaut-management").withoutVersion()
            library("http", "io.micronaut", "micronaut-http").withoutVersion()
            library("http-client", "io.micronaut", "micronaut-http-client").withoutVersion()
        }

        create("testLibs") {
            val kotestApiVersion = "5.5.5"

            library("kotest-framework-api", "io.kotest", "kotest-framework-api-jvm").version(kotestApiVersion)

            library(
                "kotest-extensions-testcontainers",
                "io.kotest.extensions",
                "kotest-extensions-testcontainers"
            ).version("1.3.4")

            library("testcontainers", "org.testcontainers", "testcontainers").withoutVersion()
        }
    }
}

rootProject.name = "lovely-micronaut"
