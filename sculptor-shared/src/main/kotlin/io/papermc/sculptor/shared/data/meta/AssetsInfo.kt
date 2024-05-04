package io.papermc.sculptor.shared.data.meta

import kotlinx.serialization.Serializable

@Serializable
data class AssetsInfo(
    val assetsFound: Boolean,
    val assetsDir: String?,
    val assetIndex: String?,
)
