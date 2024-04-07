package io.papermc.sculptor.shared.data.api.assets

import kotlinx.serialization.Serializable

@Serializable
data class MinecraftVersionAssetsIndex(
    val sha1: String,
    val url: String,
)
