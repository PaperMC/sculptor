package io.papermc.sculptor.shared.data.api

import kotlinx.serialization.Serializable

@Serializable
data class MinecraftLibraryDownloads(
    val artifact: MinecraftDownload,
)
