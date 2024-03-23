package io.papermc.sculptor.root.tasks

import io.papermc.sculptor.shared.MC_MANIFEST
import io.papermc.sculptor.shared.MacheExtension
import io.papermc.sculptor.shared.data.api.MinecraftManifest
import io.papermc.sculptor.shared.data.api.MinecraftVersion
import io.papermc.sculptor.shared.data.json
import io.papermc.sculptor.shared.util.dotGradleDirectory
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.lib.PersonIdent
import org.gradle.api.DefaultTask
import org.gradle.api.file.ProjectLayout
import org.gradle.api.file.RegularFile
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.UntrackedTask
import org.gradle.kotlin.dsl.getByType
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import javax.inject.Inject

@UntrackedTask(because = "CLI command")
abstract class AutoUpdate : DefaultTask() {

    @get:Inject
    abstract val layout: ProjectLayout

    @TaskAction
    fun run() {
        val mcManifestFile: RegularFile = layout.dotGradleDirectory.file(MC_MANIFEST)
        val mcManifest =
            json.decodeFromString<MinecraftManifest>(project.resources.text.fromFile(mcManifestFile).asString())

        val latestExistingVersion = findLatestExistingVersion(mcManifest)
        if (latestExistingVersion == null) {
            println("No existing versions found")
            return
        }
        println("Latest existing version: ${latestExistingVersion.id} (released ${latestExistingVersion.releaseTime})")


        val nextVersion = findNextVersion(mcManifest, latestExistingVersion)
        if (nextVersion == null) {
            println("No new versions found")
            return
        }
        println("Found new version: ${nextVersion.id} (released ${nextVersion.releaseTime})")

        val branchName = (if (nextVersion.type == "release") "ver" else "snap") + "/${nextVersion.id}"
        migrate(latestExistingVersion.id, nextVersion.id, branchName)

        changeDefaultBranchOnGh(branchName)

        // TODO build
        // TODO send webhook to discord with result
        // actually we can handle build and webhook via the new pushed branch and in the CI detect if last commit was by bot and send the webhook there
    }

    private fun findLatestExistingVersion(mcManifest: MinecraftManifest): MinecraftVersion? {
        var latestExistingVersion: MinecraftVersion? = null
        var latestTimestamp: LocalDateTime? = null
        project.subprojects.forEach { p ->
            if (p.name == "versions") return@forEach
            val mache = p.extensions.getByType(MacheExtension::class)
            val mcVersion = mcManifest.versions.firstOrNull { it.id == mache.minecraftVersion.get() }
            if (mcVersion == null) {
                println("Unknown Minecraft version ${mache.minecraftVersion.get()}")
                return@forEach
            }

            val timestamp = LocalDateTime.parse(mcVersion.releaseTime, DateTimeFormatter.ISO_DATE_TIME)
            if (latestTimestamp == null || timestamp > latestTimestamp) {
                latestTimestamp = LocalDateTime.parse(mcVersion.releaseTime, DateTimeFormatter.ISO_DATE_TIME)
                latestExistingVersion = mcVersion
            }
        }
        return latestExistingVersion
    }

    private fun findNextVersion(
        mcManifest: MinecraftManifest,
        latestExistingVersion: MinecraftVersion
    ): MinecraftVersion? {
        val nextVersionIndex = mcManifest.versions.indexOf(latestExistingVersion) - 1
        if (nextVersionIndex < 0) {
            println("No new versions found")
            return null
        }

        return mcManifest.versions.getOrNull(nextVersionIndex)
    }

    private fun migrate(from: String, to: String, branchName: String) {
        val git = Git.open(layout.projectDirectory.asFile)
        git.checkout().setCreateBranch(true).setName(branchName).call()

        val myTask = project.tasks.getByName("migrate") as MigrateVersion
        myTask.fromVersion.set(from)
        myTask.toVersion.set(to)
        myTask.actions.forEach { it.execute(myTask) }

        git.commit().setMessage("Update to $to").setAuthor(PersonIdent("Sculptor", "sculptor@automated.papermc.io"))
            .call()
        git.push().call()
    }

    private fun changeDefaultBranchOnGh(branchName: String) {
        val exec = Runtime.getRuntime().exec(
            arrayOf(
                "gh",
                "api",
                "repos/PaperMC/mache",
                "--method",
                "PATCH",
                "--field",
                "default_branch=$branchName"
            )
        )

        exec.inputStream.copyTo(System.out)
        exec.errorStream.copyTo(System.err)

        val exitCode = exec.waitFor()
        if (exitCode != 0) {
            println("Couldn't change default branch on GitHub, exit code: $exitCode")
        }
    }
}
