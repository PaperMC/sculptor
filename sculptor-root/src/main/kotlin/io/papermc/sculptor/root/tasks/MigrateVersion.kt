package io.papermc.sculptor.root.tasks

import io.papermc.sculptor.shared.util.convertToPath
import java.nio.file.Path
import javax.inject.Inject
import org.eclipse.jgit.api.Git
import org.gradle.api.DefaultTask
import org.gradle.api.file.ProjectLayout
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.UntrackedTask
import org.gradle.api.tasks.options.Option
import kotlin.io.path.*

@UntrackedTask(because = "CLI command, always migrate")
abstract class MigrateVersion : DefaultTask() {

    @get:Option(option = "from", description = "The version to migrate.")
    @get:Input
    abstract val fromVersion: Property<String>

    @get:Option(option = "to", description = "The new version to migrate to.")
    @get:Input
    abstract val toVersion: Property<String>

    @get:Inject
    abstract val layout: ProjectLayout

    @TaskAction
    fun run() {
        val from = fromVersion.get()
        val to = toVersion.get()

        val projDir = layout.projectDirectory.convertToPath()
        val versionsDir = projDir.resolve("versions")

        migrate(to, versionsDir.resolve("$from-server"), versionsDir.resolve("$to-server"), projDir)
        migrate(to, versionsDir.resolve("$from-client"), versionsDir.resolve("$to-client"), projDir)
    }

    private fun migrate(to: String, fromDir: Path, toDir: Path, projDir: Path) {
        if (fromDir.notExists()) {
            throw Exception("--from-version directory does not exist: ${fromDir.name}")
        }
        if (toDir.exists()) {
            throw Exception("Cannot migrate version, target already exists: ${toDir.name}")
        }

        // first we need to modify the `.gitignore` so everything we do gets tracked by git properly
        modifyGitIgnoreFile(toDir.name)

        toDir.createDirectories()
        fromDir.resolve("patches").copyToRecursively(toDir.resolve("patches"), followLinks = false, overwrite = false)
        val newBuildGradle = toDir.resolve("build.gradle.kts")
        fromDir.resolve("build.gradle.kts").copyTo(newBuildGradle)

        modifyBuildGradle(newBuildGradle, to)

        // make sure the changes are registered by git properly to maintain git history
        val git = Git.open(projDir.toFile())
        git.rm()
            .setCached(true)
            .addFilepattern("versions/${fromDir.name}")
            .call()

        git.add()
            .addFilepattern("versions/${toDir.name}")
            .addFilepattern(".gitignore")
            .call()
    }

    private fun modifyGitIgnoreFile(newVersionFolder: String) {
        val versionExclude = Regex("^!versions/.+$")

        val gitignoreFile = layout.projectDirectory.file(".gitignore").convertToPath()
        val tmpGitignoreFile = gitignoreFile.resolveSibling(".tmp_gitignore")

        tmpGitignoreFile.bufferedWriter().use { writer ->
            gitignoreFile.bufferedReader().use { reader ->
                for (line in reader.lineSequence()) {
                    writer.append(versionExclude.replace(line, "!versions/$newVersionFolder"))
                    writer.newLine()
                }
            }
        }

        tmpGitignoreFile.moveTo(gitignoreFile, overwrite = true)
    }

    private fun modifyBuildGradle(buildGradle: Path, newVersion: String) {
        val regex = Regex("^\\s*minecraftVersion\\s*=\\s*\"(.*)\"$")

        val tmpBuildGradle = buildGradle.resolveSibling(".tmp_build.gradle.kts")

        tmpBuildGradle.bufferedWriter().use { writer ->
            buildGradle.bufferedReader().use { reader ->
                for (line in reader.lineSequence()) {
                    val result = regex.matchEntire(line)
                    if (result == null) {
                        writer.append(line)
                    } else {
                        val existingVersion = result.groupValues[1]
                        writer.append(line.replace(existingVersion, newVersion))
                    }
                    writer.newLine()
                }
            }
        }

        tmpBuildGradle.moveTo(buildGradle, overwrite = true)
    }
}
