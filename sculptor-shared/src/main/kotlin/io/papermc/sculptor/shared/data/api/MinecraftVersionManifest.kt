package io.papermc.sculptor.shared.data.api

import io.papermc.sculptor.shared.data.api.assets.MinecraftVersionAssetsIndex
import kotlinx.serialization.Serializable

@Serializable
data class MinecraftVersionManifest(
    val downloads: MinecraftVersionDownloads,
    val javaVersion: MinecraftJavaVersion,
    val libraries: List<MinecraftLibrary>,
    val assetIndex: MinecraftVersionAssetsIndex,
)
