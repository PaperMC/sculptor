package io.papermc.sculptor.shared

import io.papermc.sculptor.shared.util.MacheRepo
import io.papermc.sculptor.shared.util.MinecraftJarType
import org.gradle.api.Action
import org.gradle.api.ExtensiblePolymorphicDomainObjectContainer
import org.gradle.api.NamedDomainObjectContainer
import org.gradle.api.PolymorphicDomainObjectContainer
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.kotlin.dsl.domainObjectContainer
import org.gradle.kotlin.dsl.listProperty
import org.gradle.kotlin.dsl.newInstance
import org.gradle.kotlin.dsl.polymorphicDomainObjectContainer
import org.gradle.kotlin.dsl.property
import org.gradle.kotlin.dsl.register

abstract class MacheExtension(objects: ObjectFactory) {
    /**
     * The version of Minecraft which will serve as the base.
     */
    val minecraftVersion: Property<String> = objects.property()

    /**
     * The side of the game to decompile
     */
    val minecraftJarType: Property<MinecraftJarType> = objects.property()

    /**
     * Base arguments passed to the decompiler.
     */
    val decompilerArgs: ListProperty<String> = objects.listProperty()

    /**
     * Arguments passed to the remapper. For the available placeholder see [io.papermc.sculptor.version.tasks.RemapJar].
     */
    val remapperArgs: ListProperty<String> = objects.listProperty()

    /**
     * Extra compile-time dependencies to inject to Minecraft.
     *
     * Mojang only provides the flattened runtime classpath.
     */
    val extraCompileDependencies: ListProperty<String> = objects.listProperty<String>().convention(
        listOf("com.google.code.findbugs:jsr305:3.0.2")
    )

    val runs: PolymorphicDomainObjectContainer<MinecraftRunConfiguration> = objects.polymorphicDomainObjectContainer(MinecraftRunConfiguration::class)

    fun runServer(op: Action<MinecraftRunConfiguration.Server>) {
        runs.named("runServer", MinecraftRunConfiguration.Server::class.java, op)
    }

    fun runClient(op: Action<MinecraftRunConfiguration.Client>) {
        runs.named("runClient", MinecraftRunConfiguration.Client::class.java, op)
    }

    /**
     * Maven repositories needed to resolve the configurations necessary to run mache. The configurations are
     * `codebook`, `paramMappings`, `constants`, `remapper`, and `decompiler`.
     *
     * These are defined in this way because we need this information for the metadata file we generate. Repositories
     * defined in the normal Gradle style will not be reported in the metadata file.
     */
    val repositories: NamedDomainObjectContainer<MacheRepo> = objects.domainObjectContainer(MacheRepo::class)

    init {
        val extensible = (runs as ExtensiblePolymorphicDomainObjectContainer)
        extensible.registerFactory(MinecraftRunConfiguration.Server::class.java) { name ->
            objects.newInstance(MinecraftRunConfiguration.Server::class, name)
        }
        extensible.registerFactory(MinecraftRunConfiguration.Client::class.java) { name ->
            objects.newInstance(MinecraftRunConfiguration.Client::class, name)
        }
        runs.register("runServer", MinecraftRunConfiguration.Server::class)
        runs.register("runClient", MinecraftRunConfiguration.Client::class)

        decompilerArgs.convention(
            listOf(
                // Treat some known structures as synthetic even when not explicitly set
                "--synthetic-not-set=true",
                // Fold branches of ternary expressions that have boolean true and false constants
                "--ternary-constant-simplification=true",
                // Give the decompiler information about the Java runtime
                "--include-runtime=current",
                // Decompile complex constant-dynamic expressions:
                // Some constant-dynamic expressions can't be converted to a single Java expression with
                // identical run-time behaviour. This decompiles them to a similar non-lazy expression,
                // marked with a comment
                "--decompile-complex-constant-dynamic=true",
                // Indent String
                "--indent-string=    ",
                // Process inner classes and add them to the decompiled output.
                "--decompile-inner=true", // default
                // Removes any methods that are marked as bridge from the decompiled output.
                "--remove-bridge=true", // default
                // Decompile generics in classes, methods, fields, and variables.
                "--decompile-generics=true", // default
                // Encode non-ASCII characters in string and character literals as Unicode escapes.
                "--ascii-strings=false", // default
                // Removes any methods and fields that are marked as synthetic from the decompiled output.
                "--remove-synthetic=true", // default
                // Give the decompiler information about every jar on the classpath.
                "--include-classpath=true",
                // Remove braces on simple, one line, lambda expressions.
                "--inline-simple-lambdas=true", // default
                // Ignore bytecode that is malformed.
                "--ignore-invalid-bytecode=false", // default
                // Map Bytecode to source lines.
                "--bytecode-source-mapping=true",
                // Dump line mappings to output archive zip entry extra data.
                "--dump-code-lines=true",
                // Display override annotations for methods known to the decompiler
                "--override-annotation=false",
                // Skip copying non-class files from the input folder or file to the output
                "--skip-extra-files=true",
            ),
        )

        for (repo in DEFAULT_REPOS) {
            repositories.register(repo.name) {
                url.set(repo.url)
                includeGroups.addAll(repo.includeGroups)
            }
        }
    }
}
