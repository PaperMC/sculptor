package io.papermc.sculptor.version.tasks

import io.papermc.sculptor.shared.MACHE_DIR
import io.papermc.sculptor.shared.MC_VERSION
import io.papermc.sculptor.shared.data.api.MinecraftVersionManifest
import io.papermc.sculptor.shared.data.api.assets.MinecraftVersionAssetsManifest
import io.papermc.sculptor.shared.data.json
import io.papermc.sculptor.shared.util.ClientAssetsMode
import io.papermc.sculptor.shared.util.HashingAlgorithm
import io.papermc.sculptor.shared.util.asHexString
import io.papermc.sculptor.shared.util.dotGradleDirectory
import io.papermc.sculptor.shared.util.hashFile
import io.papermc.sculptor.shared.data.meta.AssetsInfo
import io.papermc.sculptor.shared.util.Hash
import io.papermc.sculptor.shared.util.convertToPath
import io.papermc.sculptor.shared.util.download
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.ProjectLayout
import org.gradle.api.file.RegularFile
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.internal.project.ProjectInternal
import org.gradle.api.provider.Property
import org.gradle.api.resources.TextResource
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.UntrackedTask
import org.gradle.internal.logging.progress.ProgressLoggerFactory
import java.util.Locale
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import kotlin.io.path.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.isDirectory
import kotlin.io.path.isRegularFile
import kotlin.io.path.readText

@Suppress("LeakingThis")
@UntrackedTask(because = "Must execute before each run")
abstract class SetupAssets : DefaultTask() {

    @get:Inject
    abstract val layout: ProjectLayout

    @get:Input
    abstract val hashCheck: Property<Boolean>

    @get:OutputFile
    abstract val infoFile: RegularFileProperty

    @get:Input
    abstract val mode: Property<ClientAssetsMode>

    @get:InputFile
    abstract val assetsManifestFile: RegularFileProperty

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    private val progressLoggerFactory = (project as ProjectInternal).services.get(ProgressLoggerFactory::class.java)

    init {
        infoFile.convention(layout.buildDirectory.file("$MACHE_DIR/$name.json"))
    }

    @TaskAction
    fun run() {
        val outputFile = infoFile.get().asFile
        outputFile.parentFile.mkdirs()
        outputFile.delete()

        val versionManifestFile: RegularFile = layout.dotGradleDirectory.file(MC_VERSION)
        val versionManifest = json.decodeFromString<MinecraftVersionManifest>(versionManifestFile.asFile.readText())

        val info = determineClientAssetDirectories(
            versionManifest,
            hashCheck.get()
        )

        if (mode.get() != ClientAssetsMode.AUTO) {
            outputFile.writeText("")
        } else {
            outputFile.writeText(json.encodeToString(info))
        }

        if ((mode.get() == ClientAssetsMode.AUTO && !info.assetsFound) || mode.get() == ClientAssetsMode.DOWNLOADED) {
            downloadAssets()
        }
    }

    private fun determineClientAssetDirectories(
        mcManifest: MinecraftVersionManifest,
        hashCheck: Boolean
    ): AssetsInfo {
        println("Determining client asset directories")

        val windowsPathsToCheck = listOf(
            "%appdata%\\.minecraft\\assets", // vanilla
            "%appdata%\\PrismLauncher\\assets", // prism launcher
            "%appdata%\\com.modrinth.theseus\\meta\\assets", // modrinth launcher
            "~\\scoop\\persist\\multimc\\assets" // multimc
        )
        val linuxPathsToCheck = listOf(
            "~/.minecraft/assets", // vanilla
            "\$XDG_DATA_HOME/PrismLauncher/assets", // prism launcher
            "~/.var/app/org.prismlauncher.PrismLauncher/data/PrismLauncher/assets", // flatpak, prism launcher
            "\$XDG_CONFIG_HOME/com.modrinth.theseus/meta/assets", // modrinth launcher
            "\$XDG_DATA_HOME/multimc/assets/" // multimc
        )
        val macosPathsToCheck = listOf(
            "~/Library/Application Support/minecraft/assets", // vanilla
            "~/Library/Application Support/PrismLauncher/assets", // prism launcher
        )

        val osName = System.getProperty("os.name").lowercase()

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
                        listOf(
                            it.replaceFirst("\$XDG_DATA_HOME", xdgDataHome), it.replaceFirst(
                                "\$XDG_DATA_HOME",
                                "$homeDir/.local/share"
                            )
                        )
                    } else {
                        listOf(it.replaceFirst("\$XDG_DATA_HOME", "$homeDir/.local/share"))
                    }
                } else if (it.startsWith("\$XDG_CONFIG_HOME")) {
                    val xdgConfigHome = System.getenv("XDG_CONFIG_HOME") ?: ""
                    if (xdgConfigHome.isNotEmpty()) {
                        listOf(
                            it.replaceFirst("\$XDG_CONFIG_HOME", xdgConfigHome), it.replaceFirst(
                                "\$XDG_CONFIG_HOME",
                                "$homeDir/.config"
                            )
                        )
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
                // if starts with ~ then replace with home dir
                val homeDir = System.getProperty("user.home")

                if (it.startsWith("~")) {
                    return@flatMap listOf(it.replaceFirst("~", homeDir))
                }

                if (it.startsWith("%appdata%")) {
                    val appData = System.getenv("APPDATA") ?: ""
                    if (appData.isEmpty()) {
                        listOf(it.replaceFirst("%appdata%", "$homeDir\\AppData\\Roaming"))
                    } else {
                        listOf(it.replaceFirst("%appdata%", "$homeDir\\AppData\\Roaming"), it.replaceFirst("%appdata%", appData))
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
                externalVersionManifestData = json.decodeFromString<MinecraftVersionAssetsManifest>(externalVersionManifest.readText())
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
                return AssetsInfo(true, dir.toAbsolutePath().toString(), mcManifest.assets)
            } else {
                // not all present, and we dont want to touch other launchers stuff, so invalidate it
                continue
            }

        }
        // no discovered directories are valid, so we will download the assets
        println("No valid assets directories found, falling back to downloading assets.")
        return AssetsInfo(false, null, null)
    }

    private fun downloadAssets() {
        val assetManifestResource: TextResource = project.resources.text.fromFile(assetsManifestFile)
        val mcVersionAssetManifest = json.decodeFromString<MinecraftVersionAssetsManifest>(assetManifestResource.asString())

        println("Starting asset download...")
        val progress = progressLoggerFactory.newOperation("assets_download${System.currentTimeMillis()}")
        val noAssets = mcVersionAssetManifest.objects.size
        progress.start("Download assets...", "0/$noAssets")
        val completed = AtomicInteger(0)
        runBlocking {
            val deferreds = mcVersionAssetManifest.objects.map { (name, obj) ->
                val output = outputDir.file(name)
                output.convertToPath().parent.createDirectories()
                project.download.downloadAsync(
                    "https://resources.download.minecraft.net/${obj.hash.substring(0, 2)}/${obj.hash}",
                    output,
                    Hash(obj.hash, HashingAlgorithm.SHA1)
                ) {
                    progress.progress("${completed.incrementAndGet()}/$noAssets")
                }
            }
            deferreds.awaitAll()
        }
        progress.completed("$noAssets/$noAssets", false)
        println("All assets up to date.")
    }
}
