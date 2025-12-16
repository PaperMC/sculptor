package io.papermc.sculptor.shared.data.api

import kotlinx.serialization.Serializable

@Serializable
data class MinecraftVersionDownloads(
    val server: MinecraftDownload,
    val client: MinecraftDownload,
)
