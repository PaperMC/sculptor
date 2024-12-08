package io.papermc.sculptor.version

import io.papermc.sculptor.shared.*
import io.papermc.sculptor.shared.data.LibrariesList
import io.papermc.sculptor.shared.data.api.MinecraftDownload
import io.papermc.sculptor.shared.data.api.MinecraftManifest
import io.papermc.sculptor.shared.data.api.MinecraftVersionManifest
import io.papermc.sculptor.shared.data.json
import io.papermc.sculptor.shared.util.*
import io.papermc.sculptor.version.tasks.SetupAssets
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
import org.gradle.kotlin.dsl.withType
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
        val mcJarSide = mache.minecraftJarType.getOrElse(MinecraftJarType.SERVER)


        download.download(mcVersion.url, mcVersionManifestFile, Hash(mcVersion.sha1, HashingAlgorithm.SHA1))

        val manifestResource: TextResource = resources.text.fromFile(mcVersionManifestFile)
        val mcVersionManifest = json.decodeFromString<MinecraftVersionManifest>(manifestResource.asString())

        val mcVersionAssetManifestFile: RegularFile = layout.dotGradleDirectory.file(MC_VERSION_ASSET_INDEX)
        download.download(mcVersionManifest.assetIndex.url, mcVersionAssetManifestFile, Hash(mcVersionManifest.assetIndex.sha1, HashingAlgorithm.SHA1))

        tasks.withType(SetupAssets::class).configureEach {
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

        if (mcJarSide == MinecraftJarType.SERVER) {
            val librariesFile = layout.dotGradleDirectory.file(INPUT_LIBRARIES_LIST)
            val libraries = determineLibraries(downloadInputJarFile, inputJarHash, librariesFile)

            dependencies {
                for (library in libraries) {
                    "minecraft"(library) {
                        isTransitive = false
                    }
                    "serverRuntimeDependencies"(library) {
                        isTransitive = false
                    }
                    "serverCompileDependencies"(library) {
                        isTransitive = false
                    }
                }
            }
        } else {
            dependencies {
                for (library in mcVersionManifest.libraries) {
                    "minecraft"(library.name) {
                        isTransitive = false
                    }
                }
            }
        }
    }

    private fun Project.downloadInputFiles(
        download: DownloadService,
        manifest: MinecraftVersionManifest,
        inputJar: Any,
        inputMappings: Any,
        inputJarSide: MinecraftJarType
    ) {
        val inputJarDownload: MinecraftDownload
        val inputMappingsDownload: MinecraftDownload

        if (inputJarSide == MinecraftJarType.SERVER) {
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

    private fun Project.log(msg: String) {
        println("$path > $msg")
    }
}
