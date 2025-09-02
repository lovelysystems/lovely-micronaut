// apply the micronaut plugin version from the props file
// see https://docs.gradle.org/5.6/userguide/plugins.html#sec:plugin_version_management
pluginManagement {

    plugins {
        val kotlinVersion: String by settings
        kotlin("jvm") version kotlinVersion
        kotlin("plugin.allopen") version kotlinVersion
        kotlin("kapt") version kotlinVersion

        val micronautGradlePluginVersion: String by settings
        id("io.micronaut.minimal.library") version micronautGradlePluginVersion

        id("com.lovelysystems.gradle") version "1.12.0"
        id("io.gitlab.arturbosch.detekt") version "1.23.6"
        id("com.github.johnrengelman.shadow") version "8.1.1"
        id("org.jetbrains.kotlinx.kover") version "0.7.0-Alpha"
    }
}

plugins {
    // cannot use micronautGradlePluginVersion here
    id("io.micronaut.platform.catalog") version "4.3.7"
}

dependencyResolutionManagement {

    // Centralized repository definitions
    repositories {
        mavenCentral()
    }

    // Catalogs
    versionCatalogs {
        create("libs") {
            library("logstash-logback-encoder", "net.logstash.logback", "logstash-logback-encoder").version("7.4")
        }
        create("testLibs") {
            val kotestApiVersion = "5.8.0"

            library("kotest-framework-api", "io.kotest", "kotest-framework-api-jvm").version(kotestApiVersion)
            library("kotest-assertions-core", "io.kotest", "kotest-assertions-core").version(kotestApiVersion)
            library("microutils-logging", "io.github.microutils", "kotlin-logging-jvm").version("3.0.5")
            library("testcontainers-kafka", "org.testcontainers", "kafka").version("1.21.3")
            library("commons-codec", "commons-codec", "commons-codec").version("1.19.0")
            library("kafka-clients", "org.apache.kafka", "kafka-clients").version("3.9.1")
        }
    }
}

rootProject.name = "lovely-micronaut"
