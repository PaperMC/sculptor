package io.papermc.sculptor.version.tasks

import io.papermc.sculptor.shared.util.convertToPath
import io.papermc.sculptor.shared.util.ensureClean
import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.ProjectLayout
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.*
import org.gradle.process.ExecOperations
import javax.inject.Inject
import kotlin.io.path.name
import kotlin.io.path.outputStream

@CacheableTask
abstract class RunCodebook : DefaultTask() {

    @get:PathSensitive(PathSensitivity.NONE)
    @get:InputFile
    abstract val inputJar: RegularFileProperty

    @get:Input
    abstract val codebookArgs: ListProperty<String>

    @get:Classpath
    abstract val codebookClasspath: ConfigurableFileCollection

    @get:CompileClasspath
    abstract val minecraftClasspath: ConfigurableFileCollection

    @get:Classpath
    abstract val constants: ConfigurableFileCollection

    @get:OutputFile
    abstract val outputJar: RegularFileProperty

    @get:OutputDirectory
    abstract val reportsDir: DirectoryProperty

    @get:Inject
    abstract val exec: ExecOperations

    @get:Inject
    abstract val layout: ProjectLayout

    @get:Input
    abstract val memory: Property<String>

    init {
        memory.convention("2G")
    }

    @TaskAction
    fun run() {
        val out = outputJar.convertToPath().ensureClean()

        val logFile = out.resolveSibling("${out.name}.log")

        logFile.outputStream().buffered().use { log ->
            exec.javaexec {
                classpath(codebookClasspath.singleFile)

                maxHeapSize = memory.get()

                codebookArgs.get().forEach { arg ->
                    args(arg
                        .replace(Regex("\\{tempDir}")) { layout.buildDirectory.dir(".tmp_codebook").get().asFile.absolutePath }
                        .replace(Regex("\\{constantsFile}")) { constants.singleFile.absolutePath }
                        .replace(Regex("\\{output}")) { outputJar.get().asFile.absolutePath }
                        .replace(Regex("\\{input}")) { inputJar.get().asFile.absolutePath }
                        .replace(Regex("\\{inputClasspath}")) { minecraftClasspath.files.joinToString(":") { it.absolutePath } }
                        .replace(Regex("\\{reportsDir}")) { reportsDir.get().asFile.absolutePath }
                    )
                }

                standardOutput = log
                errorOutput = log
            }
        }
    }
}
