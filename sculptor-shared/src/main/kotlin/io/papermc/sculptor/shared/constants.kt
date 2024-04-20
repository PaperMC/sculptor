package io.papermc.sculptor.shared

import io.papermc.sculptor.shared.util.SimpleMacheRepo

const val REPO_URL = "https://repo.papermc.io/repository/maven-releases/"

const val GRADLE_DIR = ".gradle"
const val MACHE_DIR = "mache"
const val JSONS_DIR = "$MACHE_DIR/jsons"

const val MC_MANIFEST = "$JSONS_DIR/McManifest.json"
const val MC_VERSION = "$JSONS_DIR/McVersion.json"

const val SERVER_DIR = "$MACHE_DIR/server"
const val DOWNLOAD_SERVER_JAR = "$SERVER_DIR/download_server.jar"
const val SERVER_JAR = "$SERVER_DIR/server.jar"
const val SERVER_MAPPINGS = "$SERVER_DIR/server_mappings.txt"
const val SERVER_LIBRARIES_LIST = "$SERVER_DIR/server_libraries.json"

const val REPORTS_DIR = "$MACHE_DIR/reports"

const val REMAPPED_JAR = "$SERVER_DIR/remapped.jar"
const val DECOMP_JAR = "$SERVER_DIR/decomp.jar"
const val DECOMP_CFG = "$SERVER_DIR/decomp.cfg"

const val PATCHED_JAR = "$SERVER_DIR/patched.jar"
const val FAILED_PATCH_JAR = "$SERVER_DIR/failed_patch.jar"

val DEFAULT_REPOS: List<SimpleMacheRepo> = listOf(
    // codebook
    SimpleMacheRepo("https://repo.papermc.io/repository/maven-public/", "PaperMC", listOf("io.papermc")),
    // parchment mappings
    SimpleMacheRepo("https://maven.parchmentmc.org/", "ParchmentMC", listOf("org.parchmentmc")),
    // remapper
    SimpleMacheRepo("https://maven.neoforged.net/releases/", "NeoForged", listOf("net.neoforged", "net.minecraftforge")),
    // unpick
    SimpleMacheRepo("https://maven.fabricmc.net/", "FabricMC", listOf("net.fabricmc")),
)
