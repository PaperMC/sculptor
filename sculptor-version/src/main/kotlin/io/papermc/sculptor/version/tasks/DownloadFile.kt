package io.papermc.sculptor.version.tasks

import io.papermc.sculptor.shared.util.DownloadService
import io.papermc.sculptor.shared.util.convertToPath
import io.papermc.sculptor.shared.util.ensureClean
import org.gradle.api.DefaultTask
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.services.ServiceReference
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction

@CacheableTask
abstract class DownloadFile : DefaultTask() {
    @get:ServiceReference("download")
    abstract val downloadService: Property<DownloadService>

    @get:Input
    abstract val url: Property<String>

    @get:Input
    abstract val ext: Property<String>

    @get:OutputFile
    abstract val outputFile: RegularFileProperty

    init {
        init()
    }

    private fun init() {
        ext.convention("jar")
        outputFile.convention(
            project.layout.buildDirectory.file(
                "downloads/$name.${ext.get()}"
            )
        )
    }

    @TaskAction
    fun run () {
        val out = outputFile.convertToPath().ensureClean()
        downloadService.get().download(url.get(), out)
    }
}
