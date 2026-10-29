plugins {
    `kotlin-dsl`
}

repositories {
    maven("https://maven.fabricmc.net/")
    mavenCentral()
    gradlePluginPortal()
}

fun plugin(id: String, version: String) = "$id:$id.gradle.plugin:$version"

val loom = "1.19.3"

dependencies {
    implementation(plugin("net.fabricmc.fabric-loom", loom))
    implementation(plugin("net.fabricmc.fabric-loom-remap", loom))
}
