package io.papermc.sculptor.shared.data.meta

import kotlinx.serialization.Serializable

@Serializable
data class MacheDependencies(
    val codebook: List<MavenArtifact>,
    val constants: List<MavenArtifact>,
    val decompiler: List<MavenArtifact>,
)
