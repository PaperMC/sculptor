package io.papermc.sculptor.shared.data.api.assets

import kotlinx.serialization.Serializable

@Serializable
data class MinecraftVersionAssetObject(
    val hash: String,
    val size: Int,
)
