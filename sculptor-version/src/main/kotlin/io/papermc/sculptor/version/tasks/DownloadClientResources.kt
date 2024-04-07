package io.papermc.sculptor.version.tasks

import io.papermc.sculptor.shared.data.api.assets.MinecraftVersionAssetsManifest
import io.papermc.sculptor.shared.data.json
import io.papermc.sculptor.shared.util.Hash
import io.papermc.sculptor.shared.util.HashingAlgorithm
import io.papermc.sculptor.shared.util.download
import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.resources.TextResource
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction

abstract class DownloadClientResources : DefaultTask() {

    @get:InputFile
    abstract val assetsManifestFile: RegularFileProperty

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @TaskAction
    fun run() {
        val assetManifestResource: TextResource = project.resources.text.fromFile(assetsManifestFile)
        val mcVersionAssetManifest = json.decodeFromString<MinecraftVersionAssetsManifest>(assetManifestResource.asString())
        mcVersionAssetManifest.objects.forEach { (name, obj) ->
            project.download.download( // TODO async
                "https://resources.download.minecraft.net/${obj.hash.substring(0, 2)}/${obj.hash}",
                outputDir.file(name),
                Hash(obj.hash, HashingAlgorithm.SHA1)
            ) {
                println("Downloaded $name")
            }
        }
    }
}
