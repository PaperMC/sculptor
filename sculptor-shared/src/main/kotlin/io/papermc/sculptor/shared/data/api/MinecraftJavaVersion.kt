package io.papermc.sculptor.shared.data.api

import kotlinx.serialization.Serializable

@Serializable
data class MinecraftJavaVersion(
    val majorVersion: Int,
)
