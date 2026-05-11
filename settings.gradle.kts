plugins {
    // Auto-download a matching JDK when the project's toolchain (jvmToolchain(21)) isn't installed.
    // Without this, building on a machine that has only JDK 25 (or any version != 21) fails with
    // "Cannot find a Java installation matching {languageVersion=21}".
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.8.0"
}

rootProject.name = "zx80"
