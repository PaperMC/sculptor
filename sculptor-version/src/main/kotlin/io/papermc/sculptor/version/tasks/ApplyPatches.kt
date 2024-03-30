package io.papermc.sculptor.version.tasks

import codechicken.diffpatch.cli.PatchOperation
import codechicken.diffpatch.match.FuzzyLineMatcher
import codechicken.diffpatch.util.LoggingOutputStream
import codechicken.diffpatch.util.PatchMode
import codechicken.diffpatch.util.archiver.ArchiveFormat
import io.papermc.sculptor.shared.util.convertToPath
import io.papermc.sculptor.shared.util.ensureClean
import io.papermc.sculptor.shared.util.writeZip
import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.ProjectLayout
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.logging.LogLevel
import org.gradle.api.provider.Property
import org.gradle.api.tasks.*
import org.gradle.api.tasks.options.Option
import java.io.PrintStream
import javax.inject.Inject
import kotlin.io.path.copyTo
import kotlin.io.path.exists
import kotlin.io.path.listDirectoryEntries

@UntrackedTask(because = "Always apply patches")
abstract class ApplyPatches : DefaultTask() {

    @get:Input
    @get:Option(
        option = "verbose",
        description = "Prints out more info about the patching process",
    )
    abstract val verbose: Property<Boolean>

    @get:Optional
    @get:InputDirectory
    abstract val patchDir: DirectoryProperty

    @get:InputFile
    abstract val inputFile: RegularFileProperty

    @get:OutputFile
    abstract val outputJar: RegularFileProperty

    @get:OutputFile
    abstract val failedPatchesJar: RegularFileProperty

    @get:Inject
    abstract val layout: ProjectLayout

    init {
        run {
            verbose.convention(false)
        }
    }

    @TaskAction
    fun run() {
        val patchesPresent = patchDir.isPresent && run {
            val patches = patchDir.convertToPath()
            patches.exists() && patches.listDirectoryEntries().isNotEmpty()
        }

        val out = outputJar.convertToPath().ensureClean()
        val failed = failedPatchesJar.convertToPath().ensureClean()

        if (!patchesPresent) {
            inputFile.convertToPath().copyTo(out)
            failed.writeZip { }
            return
        }

        val printStream = PrintStream(LoggingOutputStream(logger, LogLevel.LIFECYCLE))
        val result = PatchOperation.builder()
            .logTo(printStream)
            .basePath(inputFile.convertToPath(), ArchiveFormat.ZIP)
            .patchesPath(patchDir.convertToPath())
            .outputPath(out, ArchiveFormat.ZIP)
            .rejectsPath(failed, ArchiveFormat.ZIP)
            .level(if (verbose.get()) codechicken.diffpatch.util.LogLevel.ALL else codechicken.diffpatch.util.LogLevel.INFO)
            .mode(mode())
            .minFuzz(minFuzz())
            .summary(verbose.get())
            .build()
            .operate()

        if (!verbose.get()) {
            logger.lifecycle("Applied ${result.summary.changedFiles} patches")
        }

        if (result.exit != 0) {
            throw Exception("Failed to apply ${result.summary.failedMatches} patches")
        }
    }

    internal open fun mode(): PatchMode {
        return PatchMode.OFFSET
    }

    internal open fun minFuzz(): Float {
        return FuzzyLineMatcher.DEFAULT_MIN_MATCH_SCORE
    }
}
