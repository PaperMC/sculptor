package io.papermc.sculptor.version.tasks

import io.papermc.sculptor.shared.util.GradleMavenArtifact
import io.papermc.sculptor.shared.util.MacheRepo
import io.papermc.sculptor.shared.data.json
import io.papermc.sculptor.shared.data.meta.MacheAdditionalDependencies
import io.papermc.sculptor.shared.data.meta.MacheDependencies
import io.papermc.sculptor.shared.data.meta.MacheMeta
import io.papermc.sculptor.shared.data.meta.MacheRepository
import io.papermc.sculptor.shared.util.convertToPath
import io.papermc.sculptor.shared.util.ensureClean
import kotlinx.serialization.encodeToString
import javax.inject.Inject
import kotlin.io.path.writeText
import org.gradle.api.DefaultTask
import org.gradle.api.NamedDomainObjectContainer
import org.gradle.api.file.ProjectLayout
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Nested
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction

abstract class GenerateMacheMetadata : DefaultTask() {

    @get:Input
    abstract val macheVersion: Property<String>

    @get:Input
    abstract val minecraftVersion: Property<String>

    @get:Nested
    abstract val repos: NamedDomainObjectContainer<MacheRepo>

    @get:Nested
    abstract val codebookDeps: ListProperty<GradleMavenArtifact>

    @get:Nested
    abstract val paramMappingsDeps: ListProperty<GradleMavenArtifact>

    @get:Nested
    abstract val constantsDeps: ListProperty<GradleMavenArtifact>

    @get:Nested
    abstract val remapperDeps: ListProperty<GradleMavenArtifact>

    @get:Nested
    abstract val decompilerDeps: ListProperty<GradleMavenArtifact>

    @get:Nested
    abstract val compileOnlyDeps: ListProperty<GradleMavenArtifact>

    @get:Nested
    abstract val implementationDeps: ListProperty<GradleMavenArtifact>

    @get:Input
    abstract val decompilerArgs: ListProperty<String>

    @get:Input
    abstract val remapperArgs: ListProperty<String>

    @get:OutputFile
    abstract val outputFile: RegularFileProperty

    @get:Inject
    abstract val layout: ProjectLayout

    init {
        run {
            outputFile.convention(layout.buildDirectory.dir(name).map { it.file("$name.json") })
        }
    }

    @TaskAction
    fun run() {
        val codebook = codebookDeps.get().map { it.toMavenArtifact() }
        val paramMappings = paramMappingsDeps.get().map { it.toMavenArtifact() }
        val constants = constantsDeps.get().map { it.toMavenArtifact() }
        val remapper = remapperDeps.get().map { it.toMavenArtifact() }
        val decompiler = decompilerDeps.get().map { it.toMavenArtifact() }

        val meta = MacheMeta(
            macheVersion = macheVersion.get(),
            minecraftVersion = minecraftVersion.get(),
            dependencies = MacheDependencies(codebook, paramMappings, constants, remapper, decompiler),
            repositories = repos.map { r ->
                MacheRepository(r.url.get(), r.name, r.includeGroups.get().takeIf { it.isNotEmpty() })
            },
            decompilerArgs = decompilerArgs.get(),
            remapperArgs = remapperArgs.get(),
            additionalCompileDependencies = MacheAdditionalDependencies(
                compileOnly = compileOnlyDeps.get().map { it.toMavenArtifact() }.takeIf { it.isNotEmpty() },
                implementation = implementationDeps.get().map { it.toMavenArtifact() }.takeIf { it.isNotEmpty() },
            ).takeIf { it.compileOnly != null || it.implementation != null },
        )
        val metaJson = json.encodeToString(meta)

        outputFile.convertToPath().ensureClean().writeText(metaJson)
    }
}
