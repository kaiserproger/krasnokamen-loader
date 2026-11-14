pluginManagement {
    repositories {
        maven("https://maven.fabricmc.net/")
        maven("https://maven.neoforged.net/releases/")
        maven("https://maven.minecraftforge.net")
        maven("https://libraries.minecraft.net/")
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositories {
        maven("https://maven.fabricmc.net/")
        maven("https://maven.neoforged.net/releases/")
        maven("https://maven.minecraftforge.net")
        maven("https://libraries.minecraft.net/")
        mavenCentral()
    }
}

rootProject.name = "krasnokamen-loader"

include("bridge-core")
include("fabric-agent")
include("java-agent")
include("loader-core")

// A player adapter exists only for the Minecraft versions it was ported to (README target table):
// Fabric and NeoForge cover 26.1.2/26.2/26.3, Forge only 26.3. Including an adapter for a version it
// does not support compiles that version's platform-common sources against the wrong Minecraft.
val adapterMinecraftVersion = providers.gradleProperty("minecraftVersion").getOrElse("26.3")
include("loader-fabric")
include("loader-neoforge")
if (adapterMinecraftVersion == "26.3") {
    include("loader-forge")
}
