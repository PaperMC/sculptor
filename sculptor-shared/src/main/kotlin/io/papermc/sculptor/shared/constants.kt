package io.papermc.sculptor.shared

import io.papermc.sculptor.shared.util.SimpleMacheRepo

const val PUBLISHING_PAPER_RELEASES_URL = "https://artifactory.papermc.io/artifactory/releases"
const val CONSUMER_PAPER_RELEASES_URL = "https://repo.papermc.io/repository/maven-releases/"

const val GRADLE_DIR = ".gradle"
const val MACHE_DIR = "mache"
const val JSONS_DIR = "$MACHE_DIR/jsons"

const val MC_MANIFEST = "$JSONS_DIR/McManifest.json"
const val MC_VERSION = "$JSONS_DIR/McVersion.json"
const val MC_VERSION_ASSET_INDEX = "$JSONS_DIR/McVersionAssetIndex.json"

const val INPUT_DIR = "$MACHE_DIR/input"
const val DOWNLOAD_INPUT_JAR = "$INPUT_DIR/download_input.jar"
const val EXTRACTED_SERVER_JAR = "$INPUT_DIR/server.jar"
const val INPUT_MAPPINGS = "$INPUT_DIR/input_mappings.txt"
const val INPUT_LIBRARIES_LIST = "$INPUT_DIR/input_libraries.json"

const val DOWNLOADED_ASSETS_DIR = "$MACHE_DIR/assets"

const val REPORTS_DIR = "$MACHE_DIR/reports"

const val REMAPPED_JAR = "$INPUT_DIR/remapped.jar"
const val DECOMP_JAR = "$INPUT_DIR/decomp.jar"
const val DECOMP_CFG = "$INPUT_DIR/decomp.cfg"

const val PATCHED_JAR = "$INPUT_DIR/patched.jar"
const val FAILED_PATCH_JAR = "$INPUT_DIR/failed_patch.jar"

const val SERVER_COMPILE_CLASSPATH = "serverCompileClasspath"
const val SERVER_RUNTIME_CLASSPATH = "serverRuntimeClasspath"

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
