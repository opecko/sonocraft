// Shared logic for all Stonecutter nodes. Applied after the Loom plugin (see fabric*.gradle.kts).
plugins {
    java
}

fun prop(name: String): String = requireNotNull(findProperty(name)?.toString()) { "Missing property '$name'" }

val minecraft = prop("deps.minecraft")
val javaVersion = prop("mod.java").toInt()

// Remapping Loom (1.21.11) needs mod dependencies in mod* configurations, non-obfuscated Loom (26.x) doesn't
val obfuscated = plugins.hasPlugin("net.fabricmc.fabric-loom-remap")
val modImplementation = if (obfuscated) "modImplementation" else "implementation"
val modCompileOnly = if (obfuscated) "modCompileOnly" else "compileOnly"
val modLocalRuntime = if (obfuscated) "modLocalRuntime" else "localRuntime"

version = "${prop("mod.version")}+$minecraft"
group = prop("mod.group")
base.archivesName = prop("mod.id")

repositories {
    // YACL from isXander's maven: its POM lists the libraries YACL bundles. Modrinth's POM doesn't, and the
    // remapping Loom (1.21.11) then starts a dev client without them (NoClassDefFoundError in YACL)
    maven("https://maven.isxander.dev/releases") {
        content { includeGroup("dev.isxander") }
    }
    maven("https://maven.quiltmc.org/repository/release") {
        content { includeGroupAndSubgroups("org.quiltmc") }
    }
    // OneConfig and its dev-only dependencies
    maven("https://api.modrinth.com/maven") {
        content { includeGroup("maven.modrinth") }
    }
    maven("https://maven.terraformersmc.com/releases") {
        content { includeGroup("com.terraformersmc") }
    }
}

dependencies {
    "minecraft"("com.mojang:minecraft:$minecraft")
    modImplementation("net.fabricmc:fabric-loader:${prop("deps.fabric_loader")}")
    modImplementation("net.fabricmc.fabric-api:fabric-api:${prop("deps.fabric_api")}")
    modImplementation("dev.isxander:yet-another-config-lib:${prop("deps.yacl")}")
    // optional: only the entrypoint uses it; present at dev runtime to test the config button
    modCompileOnly("com.terraformersmc:modmenu:${prop("deps.modmenu")}")
    modLocalRuntime("com.terraformersmc:modmenu:${prop("deps.modmenu")}")

    testImplementation(platform("org.junit:junit-bom:${prop("deps.junit")}"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

// OneConfig's HUD and config API lives in jars nested inside the OneConfig mod jar. Extract the ones we compile
// against (hud, config, config-impl); at runtime the classes come from OneConfig itself, only when it is installed.
val oneconfigApi: Configuration = configurations.create("oneconfigApi") { isTransitive = false }
dependencies {
    oneconfigApi("maven.modrinth:oneconfig:${prop("deps.oneconfig_api")}")
}
val oneconfigApiDir = layout.buildDirectory.dir("oneconfig-api")
val extractOneConfigApi = tasks.register<Sync>("extractOneConfigApi") {
    val jars = oneconfigApi.incoming.files
    from(jars.elements.map { files -> files.map { zipTree(it.asFile) } }) {
        include("META-INF/jars/hud-*.jar", "META-INF/jars/config-*.jar")
        eachFile { path = name }
        includeEmptyDirs = false
    }
    into(oneconfigApiDir)
}
dependencies {
    "compileOnly"(files(oneconfigApiDir.map { it.asFileTree.matching { include("*.jar") } }).builtBy(extractOneConfigApi))
    "compileOnly"("org.jetbrains.kotlin:kotlin-stdlib:${prop("deps.kotlin_stdlib")}")
}

// Dev-only: run the client with OneConfig to check that its YACL compat picks up our config screen (PLAN §9.1):
// ./gradlew :26.3:runClient -Psonocraft.oneconfig
if (hasProperty("sonocraft.oneconfig") && findProperty("deps.oneconfig") != null) {
    dependencies {
        modLocalRuntime("maven.modrinth:oneconfig:${prop("deps.oneconfig")}")
        modLocalRuntime("maven.modrinth:fabric-language-kotlin:${prop("deps.flk")}")
        modLocalRuntime("maven.modrinth:compose-multiplatform:${prop("deps.compose_bundle")}")
    }
}

// Dev-only: player name in runClient, e.g. ./gradlew :26.3:runClient -Psonocraft.username=Steve
findProperty("sonocraft.username")?.let { name ->
    extensions.getByType<net.fabricmc.loom.api.LoomGradleExtensionAPI>().runConfigs.named("client") {
        programArgs("--username", name.toString())
    }
}

java {
    toolchain.languageVersion = JavaLanguageVersion.of(javaVersion)
    withSourcesJar()
}

tasks.withType<JavaCompile>().configureEach {
    options.release = javaVersion
    options.encoding = "UTF-8"
}

tasks.test {
    useJUnitPlatform()
}

tasks.processResources {
    val props = mapOf(
        "id" to prop("mod.id"),
        "name" to prop("mod.name"),
        "version" to project.version.toString(),
        "minecraft" to prop("mod.mc_range"),
        "java" to javaVersion.toString(),
        "loader" to prop("deps.fabric_loader"),
    )
    inputs.properties(props)
    filesMatching("fabric.mod.json") { expand(props) }
}

tasks.jar {
    from(rootProject.file("LICENSE"))
}
