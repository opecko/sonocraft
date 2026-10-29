pluginManagement {
    repositories {
        maven("https://maven.fabricmc.net/") { name = "Fabric" }
        maven("https://maven.kikugie.dev/releases") { name = "KikuGie" }
        mavenCentral()
        gradlePluginPortal()
    }
}

plugins {
    id("dev.kikugie.stonecutter") version "0.9.8"
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

stonecutter {
    create(rootProject) {
        // 26.x is not obfuscated, 1.21.11 is remapped from Mojang mappings
        fun node(version: String) {
            version(version).buildscript = if (eval(version, ">=26.1")) "fabric.gradle.kts" else "fabric.obf.gradle.kts"
        }
        node("1.21.11")
        node("26.1")
        node("26.2")
        node("26.3")
        vcsVersion = "26.3"
    }
}

rootProject.name = "sonocraft"
