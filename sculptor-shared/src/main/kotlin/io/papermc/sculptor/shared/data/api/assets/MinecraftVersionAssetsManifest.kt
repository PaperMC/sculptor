package io.papermc.sculptor.shared.data.api.assets

import kotlinx.serialization.Serializable

@Serializable
data class MinecraftVersionAssetsManifest(
    val objects: LinkedHashMap<String, MinecraftVersionAssetObject>,
)
