plugins {
    kotlin("jvm")
    id("com.lovelysystems.gradle")
    id("io.micronaut.minimal.library")
    kotlin("kapt")
    kotlin("plugin.allopen")
    `java-test-fixtures`
    id("io.gitlab.arturbosch.detekt")
    id("org.jetbrains.kotlinx.kover")
}

lovely {
    gitProject()
}

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
    version.set(micronautLibs.versions.micronaut)
    testRuntime("kotest5")
}

dependencies {

    // Kotlin
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.reactor)

    // Micronaut
    implementation(micronautLibs.http.client)
    implementation(micronautLibs.management)

}
