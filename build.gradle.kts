plugins {
    kotlin("jvm")
    id("com.lovelysystems.gradle")
    id("io.micronaut.minimal.library")
    kotlin("kapt")
    kotlin("plugin.allopen")
    `maven-publish`
    id("io.gitlab.arturbosch.detekt")
    id("org.jetbrains.kotlinx.kover")
    `java-test-fixtures`
}

lovely {
    gitProject()
}

group = "com.lovelysystems"

repositories {
    mavenCentral()
}

if (JavaVersion.current() != JavaVersion.VERSION_17) {
    // we require Java 17 here, to ensure we are always using the same version as the docker images are using
    error("Java 17 is required for this Project, found ${JavaVersion.current()}")
}



kotlin {
    jvmToolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}

kover {
    koverReport {
        verify {
            onCheck = true
            rule {
                bound {
                    minValue = 55
                    metric = kotlinx.kover.gradle.plugin.dsl.MetricType.INSTRUCTION
                }
            }
        }
    }
}

allOpen {
    annotations(
        "jakarta.inject.Singleton",
    )
}

micronaut {
    testRuntime("kotest5")
}

dependencies {

    // Kotlin
    implementation(mn.kotlinx.coroutines.core)
    implementation(mn.kotlinx.coroutines.reactor)

    // Micronaut
    implementation(mn.micronaut.http.client)
    implementation(mn.micronaut.management)

    //Testing
    testImplementation(testLibs.kotest.framework.api)
    testImplementation(testLibs.kotest.extensions.testcontainers)

    // TestContainers
    testFixturesCompileOnly(testLibs.kotest.framework.api)
    testFixturesImplementation(mn.testcontainers.core)
    testFixturesImplementation(mn.kotlin.reflect)
}

publishing {
    repositories {
        maven {
            name = "GitHubPackages"
            url = uri("https://maven.pkg.github.com/lovelysystems/lovely-micronaut")
            credentials {
                username = project.findProperty("gpr.user") as String? ?: System.getenv("GITHUB_USER")
                password = project.findProperty("gpr.key") as String? ?: System.getenv("GITHUB_TOKEN")
            }
        }
    }
    publications {
        register<MavenPublication>("gpr") {
            from(components["java"])
        }
    }
}

java {
    withJavadocJar()
    withSourcesJar()
}
