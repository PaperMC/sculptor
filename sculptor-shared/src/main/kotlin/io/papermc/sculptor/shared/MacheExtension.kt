package io.papermc.sculptor.shared

import io.papermc.sculptor.shared.util.MacheRepo
import io.papermc.sculptor.shared.util.ClientAssetsMode
import io.papermc.sculptor.shared.util.MinecraftSide
import org.gradle.api.NamedDomainObjectContainer
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.kotlin.dsl.domainObjectContainer
import org.gradle.kotlin.dsl.listProperty
import org.gradle.kotlin.dsl.property

open class MacheExtension(objects: ObjectFactory) {
    /**
     * The version of Minecraft which will serve as the base.
     */
    val minecraftVersion: Property<String> = objects.property()

    /**
     * The side of the game to decompile
     */
    val minecraftJarType: Property<MinecraftSide> = objects.property()

    /**
     * Base arguments passed to the decompiler.
     */
    val decompilerArgs: ListProperty<String> = objects.listProperty()

    /**
     * Arguments passed to the remapper. For the available placeholder see {@link io.papermc.sculptor.version.tasks.RemapJar}.
     */
    val remapperArgs: ListProperty<String> = objects.listProperty()

    /**
     * Extra Arguments passed to the main client class
     *
     * This is only applicable on the client MinecraftSide
     *
     * This does not remove any default arguments, those are configured individually.
     */
    val runClientExtraArgs: ListProperty<String> = objects.listProperty()

    /**
     * Extra Arguments passed to the main dedicated server class
     *
     * This does not remove any default arguments, those are configured individually.
     */
    val runServerExtraArgs: ListProperty<String> = objects.listProperty()

    /**
     * The directory to run the client in.
     *
     * Defaults to "runClient"
     */
    val runClientDirectory: Property<String> = objects.property()

    /**
     * The directory to run the server in.
     *
     * Defaults to "run"
     */
    val runServerDirectory: Property<String> = objects.property()

    /**
     * The mode to use for client assets.
     *
     * Defaults to AUTO
     */
    val runClientAssetsMode: Property<ClientAssetsMode> = objects.property()

    /**
     * Whether to check the hash of the client asset manifest, when using local files.
     *
     * Defaults to false, due to launchers not being fully up to date with mojang.
     *
     * This Doesn't disable checking the hash of the assets themselves, or that all files in the manifest are present.
     * This is because the assets should still be intact as specified, and the launcher may download the manifest without fetching all the assets.
     */
    val runClientAssetsHashCheck: Property<Boolean> = objects.property()

    /**
     * Whether to add a sensible `--version` argument to the client.
     *
     * defaults to true
     */
    val runClientAddVersionArg: Property<Boolean> = objects.property()

    /**
     * Whether to add a `--gameDir` argument matching the working directory to the client.
     *
     * defaults to true
     */
    val runClientAddGameDirArg: Property<Boolean> = objects.property()

    /**
     * The access token to pass to the client with the `--accessToken` argument.
     *
     * defaults to a dummy value.
     *
     * set to an empty string to disable adding the argument.
     */
    val runClientAccessTokenArg: Property<String> = objects.property()

    /**
     * Whether to add the `--nogui` argument to the server.
     *
     * defaults to true, disable if you want the built-in GUI.
     */
    val runServerAddNoGuiArg: Property<Boolean> = objects.property()


    /**
     * Maven repositories needed to resolve the configurations necessary to run mache. The configurations are
     * `codebook`, `paramMappings`, `constants`, `remapper`, and `decompiler`.
     *
     * These are defined in this way because we need this information for the metadata file we generate. Repositories
     * defined in the normal Gradle style will not be reported in the metadata file.
     */
    val repositories: NamedDomainObjectContainer<MacheRepo> = objects.domainObjectContainer(MacheRepo::class)

    init {
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

        runClientDirectory.convention("runClient")
        runServerDirectory.convention("run")

        runClientExtraArgs.convention(emptyList())
        runServerExtraArgs.convention(emptyList())

        runClientAssetsMode.convention(ClientAssetsMode.AUTO)
        runClientAssetsHashCheck.convention(false)
        runClientAddVersionArg.convention(true)
        runClientAddGameDirArg.convention(true)
        runClientAccessTokenArg.convention("42")

        runServerAddNoGuiArg.convention(true)


        for (repo in DEFAULT_REPOS) {
            repositories.register(repo.name) {
                url.set(repo.url)
                includeGroups.addAll(repo.includeGroups)
            }
        }
    }
}
