// Node 1.21.11: obfuscated Minecraft, remapped from Mojang (official) mappings
plugins {
    id("net.fabricmc.fabric-loom-remap")
    id("sonocraft-common")
}

dependencies {
    "mappings"(loom.officialMojangMappings())
}
