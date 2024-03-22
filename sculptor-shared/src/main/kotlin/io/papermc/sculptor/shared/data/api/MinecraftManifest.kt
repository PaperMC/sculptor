package io.papermc.sculptor.shared.data.api

import kotlinx.serialization.Serializable

@Serializable
data class MinecraftManifest(
    val latest: Map<String, String>,
    val versions: List<MinecraftVersion>,
)
