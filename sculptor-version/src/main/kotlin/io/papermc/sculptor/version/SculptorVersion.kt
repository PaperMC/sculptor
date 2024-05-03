package io.papermc.sculptor.version

import io.papermc.sculptor.shared.*
import io.papermc.sculptor.shared.data.json
import io.papermc.sculptor.shared.util.*
import io.papermc.sculptor.shared.data.meta.AssetsInfo
import io.papermc.sculptor.version.tasks.*
import io.papermc.sculptor.version.tasks.SetupSources
import org.gradle.accessors.dm.LibrariesForLibs
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.repositories.PasswordCredentials
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.tasks.Delete
import org.gradle.api.tasks.JavaExec
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.api.tasks.Sync
import org.gradle.api.tasks.bundling.Zip
import org.gradle.kotlin.dsl.*

class SculptorVersion : Plugin<Project> {

    override fun apply(target: Project) {
        target.apply(plugin = "java")
        target.apply(plugin = "maven-publish")

        target.gradle.sharedServices.registerIfAbsent("download", DownloadService::class) {}

        target.tasks.register("cleanMacheCache", Delete::class) {
            group = "mache"
            description = "Delete downloaded manifest and jar files from Mojang."
            delete(target.layout.dotGradleDirectory.dir(MACHE_DIR))
        }

        val mache = target.extensions.create("mache", MacheExtension::class)

        val libs: LibrariesForLibs by target.extensions

        val codebook by target.configurations.registering {
            isTransitive = false
        }
        val remapper by target.configurations.registering {
            isTransitive = false
        }
        val decompiler by target.configurations.registering {
            isTransitive = false
        }
        val paramMappings by target.configurations.registering {
            isTransitive = false
        }
        val constants by target.configurations.registering {
            isTransitive = false
        }

        val minecraft by target.configurations.registering
        target.configurations.named("implementation") {
            extendsFrom(minecraft.get())
            extendsFrom(constants.get())
        }

        val extractServerJar by target.tasks.registering(ExtractServerJar::class) {
            downloadedJar.set(target.layout.dotGradleDirectory.file(DOWNLOAD_INPUT_JAR))
            serverJar.set(target.layout.dotGradleDirectory.file(EXTRACTED_SERVER_JAR))
        }

        val remapJar by target.tasks.registering(RemapJar::class) {
            if (mache.minecraftJarType.getOrElse(MinecraftJarType.SERVER) == MinecraftJarType.SERVER) {
                inputJar.set(extractServerJar.flatMap { it.serverJar })
            } else {
                inputJar.set(layout.dotGradleDirectory.file(DOWNLOAD_INPUT_JAR))
            }

            inputMappings.set(layout.dotGradleDirectory.file(INPUT_MAPPINGS))

            remapperArgs.set(mache.remapperArgs)
            codebookClasspath.from(codebook)
            minecraftClasspath.from(minecraft)
            remapperClasspath.from(remapper)
            this.paramMappings.from(paramMappings)
            this.constants.from(constants)

            outputJar.set(layout.buildDirectory.file(REMAPPED_JAR))
            reportsDir.set(layout.buildDirectory.dir(REPORTS_DIR))
        }

        val decompileJar by target.tasks.registering(DecompileJar::class) {
            inputJar.set(remapJar.flatMap { it.outputJar })
            decompilerArgs.set(mache.decompilerArgs)

            minecraftClasspath.from(minecraft)
            this.decompiler.from(decompiler)

            outputJar.set(target.layout.buildDirectory.file(DECOMP_JAR))
        }

        val applyPatches by target.tasks.registering(ApplyPatches::class) {
            group = "mache"
            description = "Apply decompilation patches to the source."

            val patchesDir = target.layout.projectDirectory.dir("patches")
            if (patchesDir.asFile.exists()) {
                patchDir.set(patchesDir)
            }

            inputFile.set(decompileJar.flatMap { it.outputJar })
            outputJar.set(layout.buildDirectory.file(PATCHED_JAR))
            failedPatchesJar.set(layout.buildDirectory.file(FAILED_PATCH_JAR))
        }

        val setupSources by target.tasks.registering(SetupSources::class) {
            decompJar.set(decompileJar.flatMap { it.outputJar })
            // Don't use the output of applyPatches directly with a flatMap
            // That would tell Gradle that this task dependsOn applyPatches, so it
            // would no longer work as a finalizer task if applyPatches fails
            patchedJar.set(target.layout.buildDirectory.file(PATCHED_JAR))
            failedPatchJar.set(target.layout.buildDirectory.file(FAILED_PATCH_JAR))

            sourceDir.set(target.layout.projectDirectory.dir("src/main/java"))
        }

        applyPatches.configure {
            finalizedBy(setupSources)
        }

        val applyPatchesFuzzy by target.tasks.registering(ApplyPatchesFuzzy::class) {
            finalizedBy(setupSources)

            group = "mache"
            description = "Attempt to apply patches with a fuzzy factor specified by --max-fuzz=<non-negative-int>. " +
                "This is not intended for normal use."

            patchDir.set(target.layout.projectDirectory.dir("patches"))

            inputFile.set(decompileJar.flatMap { it.outputJar })
            outputJar.set(target.layout.buildDirectory.file(PATCHED_JAR))
            failedPatchesJar.set(layout.buildDirectory.file(FAILED_PATCH_JAR))
        }

        val copyResources by target.tasks.registering(Sync::class) {
            into(target.layout.projectDirectory.dir("src/main/resources"))
            if (mache.minecraftJarType.getOrElse(MinecraftJarType.SERVER) == MinecraftJarType.SERVER) {
                from(target.zipTree(extractServerJar.flatMap { it.serverJar })) {
                    exclude("**/*.class", "META-INF/**")
                }
            } else {
                from(target.zipTree(target.layout.dotGradleDirectory.file(DOWNLOAD_INPUT_JAR))) {
                    exclude("**/*.class", "META-INF/**")
                }
            }

            includeEmptyDirs = false
        }

        target.tasks.register("setup") {
            group = "mache"
            description = "Set up the full project included patched sources and resources."
            dependsOn(applyPatches, copyResources)
        }

        target.tasks.register("rebuildPatches", RebuildPatches::class) {
            group = "mache"
            description = "Rebuild decompilation patches from the current source set."
            decompJar.set(decompileJar.flatMap { it.outputJar })
            sourceDir.set(target.layout.projectDirectory.dir("src/main/java"))
            patchDir.set(target.layout.projectDirectory.dir("patches"))
        }

        mache.runs.all {
            when (this) {
                is MinecraftRunConfiguration.Server -> target.tasks.register(name, JavaExec::class) {
                    group = "mache"
                    description = "Runs the minecraft server"
                    doNotTrackState("Run server")

                    classpath = target.extensions.getByType(SourceSetContainer::class).getByName("main").runtimeClasspath

                    mainClass = "net.minecraft.server.Main"

                    standardInput = System.`in`

                    doFirst {
                        workingDir(runDirectory)

                        if (addNoGuiArg.getOrElse(true)) {
                            args("--nogui")
                        }

                        extraArgs.map {
                            args(it)
                        }

                        workingDir.mkdirs()
                    }
                }

                is MinecraftRunConfiguration.Client -> {
                    val runConfig = this
                    val setupAssets = target.tasks.register(name + "SetupAssets", SetupAssets::class) {
                        hashCheck.set(runConfig.assetsHashCheck)
                        mode.set(runConfig.assetsMode)
                    }

                    target.tasks.register(name, JavaExec::class) {
                        group = "mache"
                        description = "Runs the minecraft client"
                        doNotTrackState("Run client")

                        classpath = target.extensions.getByType(SourceSetContainer::class).getByName("main").runtimeClasspath

                        mainClass = "net.minecraft.client.main.Main"

                        standardInput = System.`in`

                        dependsOn(setupAssets)

                        doFirst {
                            if (mache.minecraftJarType.getOrElse(MinecraftJarType.SERVER) != MinecraftJarType.CLIENT) {
                                throw UnsupportedOperationException("Cannot run client with server jar type!")
                            }

                            if (addVersionArg.getOrElse(true)) {
                                args("--version", mache.minecraftVersion.get() + "-mache")
                            }

                            val runClientDirectory = runDirectory.get()

                            if (addGameDirArg.getOrElse(true)) {
                                args("--gameDir", runClientDirectory.asFile.absolutePath)
                            }

                            val accessToken = accessTokenArg.getOrElse("42")
                            if (accessToken.isNotEmpty()) {
                                args("--accessToken", accessToken)
                            }

                            val clientAssetsMode = assetsMode.getOrElse(ClientAssetsMode.AUTO)

                            val assetsInfoString = setupAssets.get().infoFile.get().asFile.readText()
                            val assetsInfo = assetsInfoString.takeIf { it.isNotBlank() }?.let { json.decodeFromString<AssetsInfo>(it) }
                            val localClientAssetsFound = assetsInfo?.assetsFound ?: false

                            if ((clientAssetsMode == ClientAssetsMode.AUTO && !localClientAssetsFound) || clientAssetsMode == ClientAssetsMode.DOWNLOADED) {
                                println("Using downloaded assets")
                                dependsOn("downloadClientAssets")
                                args(
                                    "--assetsDir",
                                    target.layout.dotGradleDirectory.dir(DOWNLOADED_ASSETS_DIR).asFile.absolutePath
                                )
                            } else if (clientAssetsMode == ClientAssetsMode.AUTO) {
                                println("Using discovered assets")
                                args("--assetsDir", assetsInfo!!.assetsDir)
                                args("--assetIndex", assetsInfo!!.assetIndex)
                            }

                            extraArgs.map {
                                args(it)
                            }

                            workingDir(runClientDirectory)

                            workingDir.mkdirs()
                        }
                    }
                }

                else -> throw UnsupportedOperationException(toString())
            }
        }


        val artifactVersionProvider = target.providers.of(ArtifactVersionProvider::class) {
            parameters {
                repoUrl.set(REPO_URL)
                mcVersion.set(mache.minecraftVersion)
                ci.set(target.providers.environmentVariable("CI").orElse("false"))
            }
        }

        val generateMacheMetadata by target.tasks.registering(GenerateMacheMetadata::class) {
            minecraftVersion.set(mache.minecraftVersion)
            macheVersion.set(artifactVersionProvider)
            repos.addAll(mache.repositories)

            decompilerArgs.set(mache.decompilerArgs)
            remapperArgs.set(mache.remapperArgs)
        }

        target.afterEvaluate {
            generateMacheMetadata.configure {
                codebookDeps.set(asGradleMavenArtifacts(codebook.get()))
                paramMappingsDeps.set(asGradleMavenArtifacts(paramMappings.get()))
                constantsDeps.set(asGradleMavenArtifacts(constants.get()))
                remapperDeps.set(asGradleMavenArtifacts(remapper.get()))
                decompilerDeps.set(asGradleMavenArtifacts(decompiler.get()))

                compileOnlyDeps.set(asGradleMavenArtifacts(configurations.named("compileOnly").get()))
                implementationDeps.set(asGradleMavenArtifacts(configurations.named("implementation").get()))
            }

            if (mache.minecraftJarType.getOrElse(MinecraftJarType.SERVER) == MinecraftJarType.CLIENT) {
                target.tasks.register("downloadClientAssets", DownloadClientAssets::class) {
                    group = "mache"
                    description = "Ensure the assets for the minecraft client are correctly set up."

                    outputDir.set(target.layout.dotGradleDirectory.dir(DOWNLOADED_ASSETS_DIR))
                }
            }
        }

        val createMacheArtifact by target.tasks.registering(Zip::class) {
            group = "mache"
            description = "Create the mache metadata artifact for publishing."

            from(generateMacheMetadata) {
                rename { "mache.json" }
            }
            into("patches") {
                from(target.layout.projectDirectory.dir("patches"))
            }

            archiveBaseName.set("mache")
            archiveVersion.set(artifactVersionProvider)
            archiveExtension.set("zip")
        }

        target.afterEvaluate {
            repositories {
                for (repository in mache.repositories) {
                    maven(repository.url) {
                        name = repository.name
                        mavenContent {
                            for (group in repository.includeGroups.get()) {
                                includeGroupAndSubgroups(group)
                            }
                        }
                    }
                }

                maven("https://libraries.minecraft.net/") {
                    name = "Minecraft"
                }
                mavenCentral()
            }

            target.configure<PublishingExtension> {
                publications {
                    register<MavenPublication>("mache") {
                        groupId = "io.papermc"
                        artifactId = "mache"
                        version = artifactVersionProvider.get()

                        artifact(createMacheArtifact)
                    }
                }

                repositories {
                    maven(REPO_URL) {
                        name = "PaperMC"
                        credentials(PasswordCredentials::class)
                    }
                }
            }

            ConfigureVersionProject.configure(project, mache)
        }
    }
}
