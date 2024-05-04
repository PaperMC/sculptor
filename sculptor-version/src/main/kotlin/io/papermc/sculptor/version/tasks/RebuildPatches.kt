package io.papermc.sculptor.version.tasks

import codechicken.diffpatch.cli.DiffOperation
import codechicken.diffpatch.util.LogLevel
import codechicken.diffpatch.util.LoggingOutputStream
import codechicken.diffpatch.util.archiver.ArchiveFormat
import io.papermc.sculptor.shared.util.convertToPath
import io.papermc.sculptor.shared.util.ensureClean
import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.ProjectLayout
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.*
import org.gradle.api.tasks.options.Option
import java.io.PrintStream
import javax.inject.Inject

@UntrackedTask(because = "Always rebuild patches")
abstract class RebuildPatches : DefaultTask() {

    @get:Input
    @get:Option(
        option = "verbose",
        description = "Prints out more info about the patching process",
    )
    abstract val verbose: Property<Boolean>

    @get:InputFile
    abstract val decompJar: RegularFileProperty

    @get:InputDirectory
    abstract val sourceDir: DirectoryProperty

    @get:Input
    abstract val contextLines: Property<Int>

    @get:OutputDirectory
    abstract val patchDir: DirectoryProperty

    @get:Inject
    abstract val layout: ProjectLayout

    init {
        run {
            contextLines.convention(3)
            verbose.convention(false)
        }
    }

    @TaskAction
    fun run() {
        val patches = patchDir.convertToPath().ensureClean()
        val sourceRoot = sourceDir.convertToPath()

        val printStream = PrintStream(LoggingOutputStream(logger, org.gradle.api.logging.LogLevel.LIFECYCLE))
        val result = DiffOperation.builder()
            .logTo(printStream)
            .aPath(decompJar.convertToPath(), ArchiveFormat.ZIP)
            .bPath(sourceRoot)
            .outputPath(patches)
            .autoHeader(true)
            .level(if (verbose.get()) LogLevel.ALL else LogLevel.INFO)
            .lineEnding("\n")
            .ignorePrefix(".git")
            .ignorePrefix("data/")
            .ignorePrefix("assets/")
            .ignorePrefix("version.json")
            .ignorePrefix("flightrecorder-config.jfc")
            .ignorePrefix("pack.png")
            .context(contextLines.get())
            .summary(verbose.get())
            .build()
            .operate()

        logger.lifecycle("Rebuilt ${result.summary.changedFiles} patches")
    }
}
