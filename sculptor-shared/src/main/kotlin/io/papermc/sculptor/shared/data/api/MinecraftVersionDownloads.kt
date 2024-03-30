package io.papermc.sculptor.shared.data.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class MinecraftVersionDownloads(
    val server: MinecraftDownload,
    val client: MinecraftDownload,
    @SerialName("server_mappings") val serverMappings: MinecraftDownload,
    @SerialName("client_mappings") val clientMappings: MinecraftDownload,
)
