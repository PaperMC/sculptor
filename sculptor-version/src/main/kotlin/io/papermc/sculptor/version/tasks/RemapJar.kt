package io.papermc.sculptor.version.tasks

import io.papermc.sculptor.shared.util.convertToPath
import io.papermc.sculptor.shared.util.ensureClean
import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.ProjectLayout
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.tasks.*
import org.gradle.process.ExecOperations
import javax.inject.Inject
import kotlin.io.path.name
import kotlin.io.path.outputStream

@CacheableTask
abstract class RemapJar : DefaultTask() {

    @get:PathSensitive(PathSensitivity.NONE)
    @get:InputFile
    abstract val inputJar: RegularFileProperty

    @get:PathSensitive(PathSensitivity.NONE)
    @get:InputFile
    abstract val inputMappings: RegularFileProperty

    @get:Input
    abstract val remapperArgs: ListProperty<String>

    @get:Classpath
    abstract val codebookClasspath: ConfigurableFileCollection

    @get:CompileClasspath
    abstract val minecraftClasspath: ConfigurableFileCollection

    @get:Classpath
    abstract val remapperClasspath: ConfigurableFileCollection

    @get:PathSensitive(PathSensitivity.NONE)
    @get:InputFiles
    @get:Optional
    abstract val paramMappings: ConfigurableFileCollection

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

    @TaskAction
    fun run() {
        val out = outputJar.convertToPath().ensureClean()

        val logFile = out.resolveSibling("${out.name}.log")

        logFile.outputStream().buffered().use { log ->
            exec.javaexec {
                classpath(codebookClasspath.singleFile)

                maxHeapSize = "2G"

                remapperArgs.get().forEach { arg ->
                    args(arg
                        .replace(Regex("\\{tempDir}")) { layout.buildDirectory.dir(".tmp_codebook").get().asFile.absolutePath }
                        .replace(Regex("\\{remapperFile}")) { remapperClasspath.singleFile.absolutePath }
                        .replace(Regex("\\{mappingsFile}")) { inputMappings.get().asFile.absolutePath }
                        .replace(Regex("\\{paramsFile}")) { paramMappings.files.singleOrNull()?.absolutePath ?: "null" }
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
