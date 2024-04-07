package io.papermc.sculptor.version

import io.papermc.sculptor.shared.*
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.Delete
import org.gradle.api.tasks.JavaExec
import org.gradle.api.tasks.Sync
import org.gradle.api.tasks.bundling.Zip
import org.gradle.kotlin.dsl.*
import io.papermc.sculptor.shared.util.*
import io.papermc.sculptor.version.tasks.ApplyPatches
import io.papermc.sculptor.version.tasks.ApplyPatchesFuzzy
import io.papermc.sculptor.version.tasks.DecompileJar
import io.papermc.sculptor.version.tasks.ExtractServerJar
import io.papermc.sculptor.version.tasks.GenerateMacheMetadata
import io.papermc.sculptor.version.tasks.RebuildPatches
import io.papermc.sculptor.version.tasks.RemapJar
import io.papermc.sculptor.version.tasks.SetupSources
import org.gradle.accessors.dm.LibrariesForLibs
import org.gradle.api.artifacts.repositories.PasswordCredentials
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.tasks.SourceSetContainer

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
            if (mache.minecraftJarType.getOrElse(MinecraftSide.SERVER) == MinecraftSide.SERVER) {
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
        }

        val copyResources by target.tasks.registering(Sync::class) {
            into(target.layout.projectDirectory.dir("src/main/resources"))
            if (mache.minecraftJarType.getOrElse(MinecraftSide.SERVER) == MinecraftSide.SERVER) {
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


        target.tasks.register("runServer", JavaExec::class) {
            group = "mache"
            description = "Runs the minecraft server"
            doNotTrackState("Run server")

            val path = target.objects.fileCollection()
            path.from(target.extensions.getByType(SourceSetContainer::class).named("main").map { it.output })
            path.from(target.configurations.named("runtimeClasspath"))
            classpath = path

            mainClass = "net.minecraft.server.Main"

            args("--nogui")

            standardInput = System.`in`

            workingDir(target.layout.projectDirectory.dir("run"))
            doFirst {
                workingDir.mkdirs()
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

            val path = target.objects.fileCollection()
            path.from(target.extensions.getByType(SourceSetContainer::class).named("main").map { it.output })
            path.from(target.configurations.named("runtimeClasspath"))
            if (mache.minecraftJarType.getOrElse(MinecraftSide.SERVER) == MinecraftSide.CLIENT) {
                target.tasks.register("runClient", JavaExec::class) {
                    group = "mache"
                    description = "Runs the minecraft client"
                    doNotTrackState("Run client")

                    classpath = path

                    mainClass = "net.minecraft.client.main.Main"

                    args("--version", mache.minecraftVersion.get() + "-mache")
                    args("--gameDir", target.layout.projectDirectory.dir("runClient").asFile.absolutePath)
                    args("--accessToken", "42")

                    standardInput = System.`in`

                    workingDir(target.layout.projectDirectory.dir("runClient"))
                    doFirst {
                        workingDir.mkdirs()
                    }
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
