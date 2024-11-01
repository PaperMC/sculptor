package io.papermc.sculptor.root.tasks

import io.papermc.sculptor.shared.util.HashingAlgorithm
import io.papermc.sculptor.shared.util.asHexString
import io.papermc.sculptor.shared.util.convertToPath
import io.papermc.sculptor.shared.util.ensureClean
import io.papermc.sculptor.shared.util.getText
import io.papermc.sculptor.shared.util.getXml
import io.papermc.sculptor.shared.util.hashFile
import io.papermc.sculptor.shared.util.useZip
import io.papermc.sculptor.shared.DEFAULT_REPOS
import io.papermc.sculptor.shared.data.json
import io.papermc.sculptor.shared.data.maven.MavenMetadata
import io.papermc.sculptor.shared.data.meta.MacheMeta
import io.papermc.sculptor.shared.data.meta.MavenArtifact
import java.lang.IllegalStateException
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse.BodyHandlers
import java.nio.file.Path
import javax.inject.Inject
import kotlin.io.path.copyToRecursively
import kotlin.io.path.createDirectories
import kotlin.io.path.deleteRecursively
import kotlin.io.path.exists
import kotlin.io.path.notExists
import kotlin.io.path.readText
import kotlin.io.path.writeText
import org.gradle.api.DefaultTask
import org.gradle.api.file.ProjectLayout
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.UntrackedTask
import org.gradle.api.tasks.options.Option
import java.util.HashSet

@UntrackedTask(because = "CLI command")
abstract class OpenVersion : DefaultTask() {

    @get:Input
    @get:Option(option = "ver", description = "The version to open.")
    abstract val versionName: Property<String>

    @get:Input
    @get:Optional
    @get:Option(option = "dir-name", description = "Optional name for the directory to create in the versions/ directory.")
    abstract val directoryName: Property<String>

    @get:Input
    @get:Option(option = "force", description = "Force delete the target directory if it exists.")
    abstract val force: Property<Boolean>

    @get:Input
    abstract val repoUrl: Property<String>

    @get:Inject
    abstract val layout: ProjectLayout

    init {
        run {
            force.convention(false)
        }
    }

    @TaskAction
    fun run() {
        val version = versionName.get()

        val client = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_2)
            .build()

        val exactVersion = determineExactVersion(client, version)
        val fullUrl = repoUrl.get().removeSuffix("/") + "/io/papermc/mache/$exactVersion/mache-$exactVersion.zip"

        val tempFile = layout.buildDirectory.file("tmp/mache-$exactVersion.zip").convertToPath().ensureClean()

        val request = HttpRequest.newBuilder().GET().uri(URI.create(fullUrl)).build()
        val response = client.send(request, BodyHandlers.ofFile(tempFile))
        if (response.statusCode() !in 200..299) {
            throw Exception("Failed to successfully download file from: $fullUrl")
        }

        val resultFile = response.body()
        checkHash(client, fullUrl, resultFile)

        resultFile.useZip { root ->
            createVersionDirectory(resultFile, root)
        }
    }

    private fun createVersionDirectory(zip: Path, root: Path) {
        val macheJson = root.resolve("mache.json")
        if (macheJson.notExists()) {
            throw Exception("Malformed mache artifact: $zip")
        }

        val macheMeta = json.decodeFromString<MacheMeta>(macheJson.readText())
        val versionsDirectory = layout.projectDirectory.dir("versions").convertToPath()

        val thisVersionName = if (directoryName.isPresent) {
            directoryName.get()
        } else {
            macheMeta.minecraftVersion
        }

        val outputDir = versionsDirectory.resolve(thisVersionName)
        if (outputDir.exists()) {
            if (force.get()) {
                outputDir.deleteRecursively()
            } else {
                throw Exception(
                    "Cannot create a new version directory of path: $outputDir, already exists. " +
                        "Specify a different directory name with --dir-name.",
                )
            }
        }

        outputDir.createDirectories()
        root.resolve("patches").copyToRecursively(outputDir.resolve("patches"), followLinks = false, overwrite = false)

        val buildGradleText = writeBuildGradle(macheMeta).replace("\n", System.lineSeparator())
        outputDir.resolve("build.gradle.kts").writeText(buildGradleText)
    }

    private fun determineExactVersion(client: HttpClient, version: String): String {
        if (version.contains("+")) {
            // appears to contain build descriptor
            // assume this is the exact version
            return version
        }

        val metaUrl = repoUrl.get().removeSuffix("/") + "/io/papermc/mache/maven-metadata.xml"
        val meta = client.getXml<MavenMetadata>(metaUrl)

        val buildIdentifier = "+build."
        val maxVersion = meta.versioning.versions.asSequence()
            // our version scheme always includes build identifiers
            .filter { it.contains(buildIdentifier) }
            // don't naively assume it.startWith() would work - pre-release versions would
            // also match
            .filter { it.substringBefore(buildIdentifier) == version }
            .maxByOrNull { it.substringAfter(buildIdentifier).toIntOrNull() ?: -1 }

        return maxVersion ?: throw IllegalStateException("Could not find matching version from: $version")
    }

    private fun writeBuildGradle(meta: MacheMeta): String = buildString {

        appendLine("import io.papermc.sculptor.shared.util.MinecraftJarType")
        appendLine()
        appendLine(
            """
            plugins {
                id("io.papermc.sculptor.version") version "${
                project.buildscript.configurations.getByName("classpath").resolvedConfiguration.resolvedArtifacts.stream()
                    .map { artifact -> artifact.moduleVersion.id }
                    .filter { id -> "sculptor-root" == id.name }
                    .findAny()
                    .map { w -> w.version }
                    .orElseThrow()
                }"
            }
            """.trimIndent(),
        )
        appendLine()

        appendLine("val generateReportsProperty = providers.gradleProperty(\"generateReports\")")

        appendLine("mache {")
        append(indent(1)).appendLine("minecraftVersion = \"${meta.minecraftVersion}\"")

        append(indent(1)).appendLine(
            "minecraftJarType = ${
                (if (meta.includesClientPatches) {
                    "MinecraftJarType.CLIENT"
                } else {
                    "MinecraftJarType.SERVER"
                })
            } "
        )
        // in most cases, repos probably won't be needed
        val defaultUrls = DEFAULT_REPOS.mapTo(HashSet()) { it.url }
        if (meta.repositories.any { it.url !in defaultUrls }) {
            appendLine()
        }

        for (rep in meta.repositories) {
            if (rep.url in defaultUrls) {
                continue
            }

            append(indent(1)).appendLine("repositories.register(\"${rep.name}\") {")
            append(indent(2)).appendLine("url = \"${rep.url}\"")

            rep.groups?.forEach { group ->
                append(indent(2)).appendLine("includeGroups.add(\"$group\")")
            }
            append(indent(1)).appendLine("}")
        }
        appendLine()

        append(indent(1)).appendLine("val args = mutableListOf(")

        if (meta.remapperArgs.isEmpty()) {
            for (arg in meta.decompilerArgs) {
                append(indent(2)).appendLine("\"$arg\",")
            }
        } else {
            for (arg in meta.remapperArgs) {
                append(indent(2)).appendLine("\"$arg\",")
            }
        }

        append(indent(1)).appendLine(")")
        appendLine()

        append(indent(1)).appendLine("if (generateReportsProperty.getOrElse(\"false\").toBooleanStrict()) {")
        append(indent(2)).appendLine("args.addAll(listOf(")
        append(indent(3)).appendLine("\"--reports-dir={reportsDir}\",")
        append(indent(3)).appendLine("\"--all-reports\",")
        append(indent(2)).appendLine("))")
        append(indent(1)).appendLine("}")

        appendLine()

        append(indent(1)).appendLine("remapperArgs.set(args)")

        appendLine("}")


        // first set of dependencies is the codebook-related dependencies
        appendLine("dependencies {")

        for (dep in meta.dependencies.codebook) {
            if (dep.matches("io.papermc.codebook:codebook-cli", classifier = "all")) {
                append(indent(1)).appendLine("codebook(\"${dep.version}\")")
            } else {
                appendMavenDep(dep, "codebook", quoteConfiguration = true)
            }
        }

        for (dep in meta.dependencies.remapper) {
            if (dep.matches("net.neoforged:AutoRenamingTool", classifier = "all")) {
                append(indent(1)).appendLine("remapper(art(\"${dep.version}\"))")
            } else {
                appendMavenDep(dep, "remapper")
            }
        }

        for (dep in meta.dependencies.decompiler) {
            if (dep.matches("org.vineflower:vineflower")) {
                append(indent(1)).appendLine("decompiler(vineflower(\"${dep.version}\"))")
            } else {
                appendMavenDep(dep, "decompiler")
            }
        }

        val mcVersionRegex = Regex("parchment-(?<mcVersion>.+)")
        for (dep in meta.dependencies.paramMappings) {
            if (dep.matches("org.parchmentmc.data", mcVersionRegex, extension = "zip")) {
                val match = mcVersionRegex.matchEntire(dep.name)!!
                val mcVersion = match.groups["mcVersion"]?.value!!
                append(indent(1)).appendLine("parchment(\"$mcVersion\", \"${dep.version}\")")
            } else {
                appendMavenDep(dep, "paramMappings")
            }
        }

        for (constant in meta.dependencies.constants) {
            appendMavenDep(constant, "constants")
        }

        appendLine("}")

        // we place additional dependencies in a separate block
        if (meta.additionalCompileDependencies?.compileOnly?.isNotEmpty() == true ||
            meta.additionalCompileDependencies?.implementation?.isNotEmpty() == true
        ) {
            appendLine()
            appendLine("dependencies {")

            meta.additionalCompileDependencies?.compileOnly?.forEach { dep ->
                appendMavenDep(dep, "compileOnly")
            }
            meta.additionalCompileDependencies?.implementation?.forEach { dep ->
                appendMavenDep(dep, "implementation")
            }

            appendLine("}")
        }
    }

    private fun StringBuilder.appendMavenDep(dep: MavenArtifact, configuration: String, quoteConfiguration: Boolean = false) {
        append(indent(1))
        if (quoteConfiguration) {
            append('"').append(configuration).append('"')
        } else {
            append(configuration)
        }
        append("""("${dep.group}:${dep.name}:${dep.version}")""")
        if (dep.isSimple()) {
            appendLine()
        } else {
            appendLine(" {")
            append(indent(2)).appendLine("artifact {")
            if (dep.classifier != null) {
                append(indent(3)).appendLine("classifier = \"${dep.classifier}\"")
            }
            if (dep.extension != null) {
                append(indent(3)).appendLine("extension = \"${dep.extension}\"")
            }
            append(indent(2)).appendLine("}")
            append(indent(1)).appendLine("}")
        }
    }

    private fun indent(n: Int): String {
        return "    ".repeat(n)
    }

    private fun MavenArtifact.matches(module: String, classifier: String? = null, extension: String? = null): Boolean {
        return group == module.substringBefore(':') &&
            name == module.substringAfter(':') &&
            classifier == this.classifier &&
            extension == this.extension
    }

    private fun MavenArtifact.matches(group: String, namePattern: Regex, classifier: String? = null, extension: String? = null): Boolean {
        return this.group == group &&
            this.name.matches(namePattern) &&
            classifier == this.classifier &&
            extension == this.extension
    }

    private fun MavenArtifact.isSimple(): Boolean {
        return classifier == null && extension == null
    }

    private fun checkHash(client: HttpClient, url: String, path: Path) {
        val expectedHash = client.getText("$url.sha256").lowercase()

        val realHash = path.hashFile(HashingAlgorithm.SHA256).asHexString().lowercase()
        if (expectedHash != realHash) {
            throw Exception("Hash does not match for downloaded file from: $url, file: $path")
        }
    }
}
