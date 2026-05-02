plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.spotless)
    application
}

group = "ru.alepar"

version = "0.1.0-SNAPSHOT"

kotlin { jvmToolchain(21) }

repositories { mavenCentral() }

dependencies {
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.clikt)

    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
    testImplementation(libs.assertj.core)
}

application {
    applicationName = "zx80"
    mainClass = "ru.alepar.zx80.cli.MainKt"
}

tasks.test {
    useJUnitPlatform()
    testLogging { events("passed", "failed", "skipped") }
}

spotless {
    kotlin {
        target("src/**/*.kt")
        ktfmt(libs.versions.ktfmt.get()).kotlinlangStyle()
    }
    kotlinGradle {
        target("*.gradle.kts")
        ktfmt(libs.versions.ktfmt.get()).kotlinlangStyle()
    }
}
