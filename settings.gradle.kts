// apply the micronaut plugin version from the props file
// see https://docs.gradle.org/5.6/userguide/plugins.html#sec:plugin_version_management
pluginManagement {
    plugins {
        // micronaut platform catalog plugin must be defined here (not in toml) as it is a settings plugin
        // https://micronaut-projects.github.io/micronaut-gradle-plugin/latest/#sec:micronaut-platform-catalog-plugin
        val micronautGradlePluginVersion: String by settings
        id("io.micronaut.minimal.library") version micronautGradlePluginVersion
        id("io.micronaut.platform.catalog") version micronautGradlePluginVersion
    }
}

plugins {
    id("io.micronaut.platform.catalog")
}

dependencyResolutionManagement {
    // Centralized repository definitions
    repositories {
        mavenCentral()
    }

}

rootProject.name = "lovely-micronaut"
