package io.papermc.sculptor.version

import io.papermc.sculptor.shared.*
import io.papermc.sculptor.shared.data.LibrariesList
import io.papermc.sculptor.shared.data.api.MinecraftDownload
import io.papermc.sculptor.shared.data.api.MinecraftManifest
import io.papermc.sculptor.shared.data.api.MinecraftVersionManifest
import io.papermc.sculptor.shared.data.api.assets.MinecraftVersionAssetsManifest
import io.papermc.sculptor.shared.data.json
import io.papermc.sculptor.shared.util.*
import io.papermc.sculptor.version.tasks.DownloadClientAssets
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import org.gradle.api.Project
import org.gradle.api.file.RegularFile
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.resources.TextResource
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.extra
import org.gradle.kotlin.dsl.withType
import java.util.*
import kotlin.io.path.*

object ConfigureVersionProject {

    fun configure(target: Project, mache: MacheExtension) {
        return target.configure0(mache)
    }

    private fun Project.configure0(mache: MacheExtension) {
        val mcManifestFile: RegularFile = rootProject.layout.dotGradleDirectory.file(MC_MANIFEST)
        val mcManifest = json.decodeFromString<MinecraftManifest>(resources.text.fromFile(mcManifestFile).asString())

        val mcVersionManifestFile: RegularFile = layout.dotGradleDirectory.file(MC_VERSION)
        val mcVersion = mcManifest.versions.firstOrNull { it.id == mache.minecraftVersion.get() }
            ?: throw RuntimeException("Unknown Minecraft version ${mache.minecraftVersion.get()}")
        val mcJarSide = mache.minecraftJarType.getOrElse(MinecraftSide.SERVER)


        download.download(mcVersion.url, mcVersionManifestFile, Hash(mcVersion.sha1, HashingAlgorithm.SHA1))

        val manifestResource: TextResource = resources.text.fromFile(mcVersionManifestFile)
        val mcVersionManifest = json.decodeFromString<MinecraftVersionManifest>(manifestResource.asString())

        val mcVersionAssetManifestFile: RegularFile = layout.dotGradleDirectory.file(MC_VERSION_ASSET_INDEX)
        download.download(mcVersionManifest.assetIndex.url, mcVersionAssetManifestFile, Hash(mcVersionManifest.assetIndex.sha1, HashingAlgorithm.SHA1))

        tasks.withType(DownloadClientAssets::class).configureEach {
            assetsManifestFile.set(mcVersionAssetManifestFile)
        }

        // must be configured now before the value of the property is read later
        configure<JavaPluginExtension> {
            toolchain {
                languageVersion.set(JavaLanguageVersion.of(mcVersionManifest.javaVersion.majorVersion))
            }
        }
        tasks.withType(JavaCompile::class).configureEach {
            options.release.set(mcVersionManifest.javaVersion.majorVersion)
        }

        val downloadInputJarFile = layout.dotGradleDirectory.file(DOWNLOAD_INPUT_JAR)
        val inputMappingsFile = layout.dotGradleDirectory.file(INPUT_MAPPINGS)

        downloadInputFiles(download, mcVersionManifest, downloadInputJarFile, inputMappingsFile, mcJarSide)

        val inputJarHash = downloadInputJarFile.convertToPath().hashFile(HashingAlgorithm.SHA256).asHexString()

        if (mcJarSide == MinecraftSide.SERVER) {
            val librariesFile = layout.dotGradleDirectory.file(INPUT_LIBRARIES_LIST)
            val libraries = determineLibraries(downloadInputJarFile, inputJarHash, librariesFile)

            dependencies {
                for (library in libraries) {
                    "minecraft"(library)
                }
            }
        } else {
            dependencies {
                for (library in mcVersionManifest.libraries) {
                    "minecraft"(library.name)
                }
            }

            val mcRunClientAssetsMode = mache.runClientAssetsMode.getOrElse(ClientAssetsMode.AUTO)

            if (mcRunClientAssetsMode == ClientAssetsMode.AUTO) {
                determineClientAssetDirectories(mcVersionManifest, mache.runClientAssetsHashCheck.getOrElse(false))

            }
        }
    }

    private fun Project.downloadInputFiles(
        download: DownloadService,
        manifest: MinecraftVersionManifest,
        inputJar: Any,
        inputMappings: Any,
        inputJarSide: MinecraftSide
    ) {
        val inputJarDownload: MinecraftDownload
        val inputMappingsDownload: MinecraftDownload

        if (inputJarSide == MinecraftSide.SERVER) {
            inputJarDownload = manifest.downloads.server
            inputMappingsDownload = manifest.downloads.serverMappings
        } else {
            inputJarDownload = manifest.downloads.client
            inputMappingsDownload = manifest.downloads.clientMappings
        }

        runBlocking {
            awaitAll(
                download.downloadAsync(
                    inputJarDownload.url,
                    inputJar,
                    Hash(inputJarDownload.sha1, HashingAlgorithm.SHA1)
                ) { log("Downloaded ${inputJarSide.name} jar") },
                download.downloadAsync(
                    inputMappingsDownload.url,
                    inputMappings,
                    Hash(inputMappingsDownload.sha1, HashingAlgorithm.SHA1),
                ) {
                    log("Downloaded ${inputJarSide.name} mappings")
                },
            )
        }
    }

    private fun Project.determineLibraries(jar: Any, serverHash: String, libraries: Any): List<String> {
        val librariesJson = libraries.convertToPath()
        val libs = if (librariesJson.exists()) {
            json.decodeFromString<LibrariesList>(resources.text.fromFile(libraries).asString())
        } else {
            null
        }

        val inputJar = jar.convertToPath()
        if (libs != null) {
            if (serverHash == libs.sha256) {
                return libs.libraries
            }
        }

        val result = inputJar.useZip { root ->
            val librariesList = root.resolve("META-INF").resolve("libraries.list")

            return@useZip librariesList.useLines { lines ->
                return@useLines lines.map { line ->
                    val parts = line.split(whitespace)
                    if (parts.size != 3) {
                        throw Exception("libraries.list file is invalid")
                    }
                    return@map parts[1]
                }.toList()
            }
        }

        val resultList = json.encodeToString(LibrariesList(serverHash, result))
        librariesJson.writeText(resultList)
        return result
    }


    private fun Project.determineClientAssetDirectories(
        mcManifest: MinecraftVersionManifest,
        hashCheck: Boolean
    ) {
        println("Determining client asset directories")

        val windowsPathsToCheck = listOf(
            "%appdata%\\.minecraft\\assets", // vanilla
            "%appdata%\\PrismLauncher\\assets", // prism launcher
            "%appdata%\\com.modrinth.theseus\\meta\\assets", // modrinth launcher
        )
        val linuxPathsToCheck = listOf(
            "~/.minecraft/assets", // vanilla
            "\$XDG_DATA_HOME/PrismLauncher/assets", // prism launcher
            "~/.var/app/org.prismlauncher.PrismLauncher/data/PrismLauncher/assets", // flatpak, prism launcher
            "\$XDG_CONFIG_HOME/com.modrinth.theseus/meta/assets" // modrinth launcher
        )
        val macosPathsToCheck = listOf(
            "~/Library/Application Support/minecraft/assets", // vanilla
            "~/Library/Application Support/PrismLauncher/assets", // prism launcher
        )

        val osName = System.getProperty("os.name").lowercase();

        val pathsToCheck = if (osName.contains("linux")) {
            linuxPathsToCheck.flatMap {
                // if starts with ~ then replace with home dir
                val homeDir = System.getProperty("user.home")

                if (it.startsWith("~")) {
                    return@flatMap listOf(it.replaceFirst("~", homeDir))
                }

                // if it starts with $XDG_DATA_HOME then replace with the env var, and a hardcoded fallback (not sure if apps are xdg compliant)
                if (it.startsWith("\$XDG_DATA_HOME")) {
                    val xdgDataHome = System.getenv("XDG_DATA_HOME") ?: ""
                    if (xdgDataHome.isNotEmpty()) {
                        listOf(it.replaceFirst("\$XDG_DATA_HOME", xdgDataHome), it.replaceFirst("\$XDG_DATA_HOME",
                            "$homeDir/.local/share"
                        ))
                    } else {
                        listOf(it.replaceFirst("\$XDG_DATA_HOME", "$homeDir/.local/share"))
                    }
                } else if (it.startsWith("\$XDG_CONFIG_HOME")) {
                    val xdgConfigHome = System.getenv("XDG_CONFIG_HOME") ?: ""
                    if (xdgConfigHome.isNotEmpty()) {
                        listOf(it.replaceFirst("\$XDG_CONFIG_HOME", xdgConfigHome), it.replaceFirst("\$XDG_CONFIG_HOME",
                            "$homeDir/.config"
                        ))
                    } else {
                        listOf(it.replaceFirst("\$XDG_CONFIG_HOME", "$homeDir/.config"))
                    }
                } else {
                    listOf(it)
                }
            }
        } else if (osName.contains("mac os x") || osName.contains("darwin") || osName.contains("osx")) {
            macosPathsToCheck.map {
                if (it.startsWith("~")) {
                    it.replaceFirst("~", System.getProperty("user.home"))
                } else {
                    it
                }
            }
        } else if (osName.contains("windows")) {
            windowsPathsToCheck.flatMap {
                if (it.startsWith("%appdata%")) {
                    val appData = System.getenv("APPDATA") ?: ""
                    if (appData.isEmpty()) {
                        listOf(it.replaceFirst("%appdata%", System.getProperty("user.home") + "\\AppData\\Roaming"))
                    } else {
                        listOf(it.replaceFirst("%appdata%", System.getProperty("user.home") + "\\AppData\\Roaming"), it.replaceFirst("%appdata%", appData))
                    }
                } else {
                    listOf(it)
                }
            }
        } else {
            emptyList()
        }

        // directories that contain "indexes" and "objects" (quick filter to see if in the right format.)
        val assetDirs = pathsToCheck.map { Path(it) }.filter { it.isDirectory() && it.resolve("indexes").isDirectory() && it.resolve("objects").isDirectory() }

        for (dir in assetDirs) {
            println("Now checking $dir for assets")
            val externalVersionManifest = dir.resolve("indexes").resolve("${mcManifest.assets}.json")
            if (!externalVersionManifest.isRegularFile()) {
                continue
            }

            if (hashCheck) {
                if (externalVersionManifest.hashFile(HashingAlgorithm.SHA1).asHexString()
                        .lowercase(Locale.ENGLISH) != mcManifest.assetIndex.sha1.lowercase(Locale.ENGLISH)
                ) {
                    println("Hash mismatch for $externalVersionManifest: expected ${mcManifest.assetIndex.sha1}, got ${externalVersionManifest.hashFile(HashingAlgorithm.SHA1).asHexString()}")
                    continue
                }
                // hash is verified, matches official download.
                // check to ensure all hashes match
            }
            val externalVersionManifestData: MinecraftVersionAssetsManifest
            try {
                 externalVersionManifestData = json.decodeFromString<MinecraftVersionAssetsManifest>(project.resources.text.fromFile(externalVersionManifest).asString())
            } catch (e: IllegalArgumentException) {
                println("Failed to parse $externalVersionManifest as a valid minecraft object manifest.")
                continue
            }


            // ensure all objects in
            val objExists = externalVersionManifestData.objects.map { (name, obj) ->
                val objFile = dir.resolve("objects/${obj.hash.substring(0, 2)}/${obj.hash}")
                if (objFile.isRegularFile()) {
                    // don't skip this hash check: ensure the file is correct as in the local manifest,
                    // which may or may not match the official manifest
                    val hashOk = objFile.hashFile(HashingAlgorithm.SHA1).asHexString()
                        .lowercase(Locale.ENGLISH) == obj.hash.lowercase(Locale.ENGLISH)
                    if (!hashOk) {
                        println("Hash mismatch for $name ($objFile): expected ${obj.hash}, got ${objFile.hashFile(HashingAlgorithm.SHA1).asHexString()}")
                        false
                    } else {
                        true
                    }
                } else {
                    println("Object $name ($objFile) does not exist or is not a file.")
                    false
                }
            }

            if (objExists.all { it } && objExists.isNotEmpty()) {
                // all objects exist and have the correct hash
                // we can use this directory

                println("Using discovered assets directory: $dir, with asset index ${mcManifest.assets}")
                project.extra.set("runClientAssetsFound", true)
                project.extra.set("runClientAssetsDir", dir.toAbsolutePath().toString())
                project.extra.set("runClientAssetIndex", mcManifest.assets)
                return
            } else {
                // not all present, and we dont want to touch other launchers stuff, so invalidate it
                continue
            }

        }
        // no discovered directories are valid, so we will download the assets
        project.extra.set("runClientAssetsFound", false)
        println("No valid assets directories found, falling back to downloading assets.")
    }


    private fun Project.log(msg: String) {
        println("$path > $msg")
    }
}
