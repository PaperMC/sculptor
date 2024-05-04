package io.papermc.sculptor.shared.data.meta

import kotlinx.serialization.Serializable

@Serializable
data class MacheMeta(
    val minecraftVersion: String,
    val includesClientPatches: Boolean,
    val macheVersion: String,
    val dependencies: MacheDependencies,
    val repositories: List<MacheRepository>,
    val decompilerArgs: List<String>,
    val remapperArgs: List<String>,
    val additionalCompileDependencies: MacheAdditionalDependencies? = null,
)
