package io.papermc.sculptor.shared.data.api

import kotlinx.serialization.Serializable

@Serializable
class MinecraftDownload(
    val url: String,
    val size: Int,
    val sha1: String,
)
