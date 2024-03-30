package io.papermc.sculptor.shared.data.api

import kotlinx.serialization.Serializable

@Serializable
data class MinecraftVersionManifest(
    val downloads: MinecraftVersionDownloads,
    val javaVersion: MinecraftJavaVersion,
    val libraries: List<MinecraftLibrary>,
)
