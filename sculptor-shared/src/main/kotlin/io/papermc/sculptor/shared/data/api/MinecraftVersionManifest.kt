package io.papermc.sculptor.shared.data.api

import kotlinx.serialization.Serializable

@Serializable
data class MinecraftVersionManifest(
    val downloads: MinecraftVersionDownloads,
    val javaVersion: MinecraftJavaVersion,
    val libraries: List<MinecraftLibrary>,
    val assetIndex: MinecraftDownload,
    val assets: String, // also present in asset index, but that would require extending MinecraftDownload.
)
