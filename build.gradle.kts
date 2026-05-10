import java.net.URL
import java.security.MessageDigest

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

val downloadRom by tasks.registering {
    val outFile = layout.buildDirectory.file("generated-resources/rom/48.rom")
    // rastersoft/fbzx is an actively-maintained ZX emulator that bundles the canonical Sinclair
    // 48K ROM. Verified live mirror returning 16384 bytes with the SHA-256 below as of 2026-05-10.
    val romUrl = "https://github.com/rastersoft/fbzx/raw/master/data/spectrum-roms/48.rom"
    val expectedSize = 16_384
    val expectedSha256 = "d55daa439b673b0e3f5897f99ac37ecb45f974d1862b4dadb85dec34af99cb42"

    outputs.file(outFile)
    outputs.upToDateWhen {
        val f = outFile.get().asFile
        f.exists() && f.length() == expectedSize.toLong() &&
            (expectedSha256.isEmpty() || sha256Hex(f.readBytes()) == expectedSha256)
    }

    doLast {
        val target = outFile.get().asFile
        target.parentFile.mkdirs()
        URL(romUrl).openStream().use { input ->
            target.outputStream().use { input.copyTo(it) }
        }
        val actualSize = target.length()
        check(actualSize == expectedSize.toLong()) {
            "downloaded $romUrl: expected $expectedSize bytes, got $actualSize"
        }
        val actualSha = sha256Hex(target.readBytes())
        if (expectedSha256.isNotEmpty()) {
            check(actualSha == expectedSha256) {
                "48.rom SHA-256 mismatch: got $actualSha, expected $expectedSha256. " +
                    "Update URL or place a verified copy at ${target.absolutePath}"
            }
        }
        logger.lifecycle("downloaded 48.rom (${actualSize} bytes, sha256=$actualSha) to $target")
    }
}

fun sha256Hex(bytes: ByteArray): String =
    MessageDigest.getInstance("SHA-256")
        .digest(bytes)
        .joinToString("") { "%02x".format(it) }

tasks.processResources {
    dependsOn(downloadRom)
    from(layout.buildDirectory.dir("generated-resources"))
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
