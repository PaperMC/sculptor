package io.papermc.sculptor.shared

import io.papermc.sculptor.shared.util.ClientAssetsMode
import org.gradle.api.Named
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.ProjectLayout
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import javax.inject.Inject

@Suppress("LeakingThis")
abstract class MinecraftRunConfiguration(layout: ProjectLayout, private val myName: String) : Named {
    override fun getName(): String {
        return myName
    }

    /**
     * Extra arguments passed to the main class
     *
     * This does not remove any default arguments, those are configured individually.
     */
    abstract val extraArgs: ListProperty<String>

    /**
     * The directory to run the client in.
     *
     * Defaults to a directory using the task name in the project directory.
     */
    abstract val runDirectory: DirectoryProperty

    init {
        extraArgs.convention(listOf())
        runDirectory.convention(layout.projectDirectory.dir(name))
    }

    abstract class Server @Inject constructor(layout: ProjectLayout, name: String) : MinecraftRunConfiguration(layout, name) {
        /**
         * Whether to add the `--nogui` argument to the server.
         *
         * Defaults to `true`, disable if you want the built-in GUI.
         */
        abstract val addNoGuiArg: Property<Boolean>

        init {
            addNoGuiArg.convention(true)
        }
    }

    abstract class Client @Inject constructor(layout: ProjectLayout, name: String) : MinecraftRunConfiguration(layout, name) {
        /**
         * The mode to use for client assets.
         *
         * Defaults to AUTO
         */
        abstract val assetsMode: Property<ClientAssetsMode>

        /**
         * Whether to check the hash of the client asset manifest, when using local files.
         *
         * Defaults to false, due to launchers not being fully up to date with mojang.
         *
         * This Doesn't disable checking the hash of the assets themselves, or that all files in the manifest are present.
         * This is because the assets should still be intact as specified, and the launcher may download the manifest without fetching all the assets.
         */
        abstract val assetsHashCheck: Property<Boolean>

        /**
         * Whether to add a sensible `--version` argument to the client.
         *
         * defaults to true
         */
        abstract val addVersionArg: Property<Boolean>

        /**
         * Whether to add a `--gameDir` argument matching the working directory to the client.
         *
         * defaults to true
         */
        abstract val addGameDirArg: Property<Boolean>

        /**
         * The access token to pass to the client with the `--accessToken` argument.
         *
         * defaults to a dummy value.
         *
         * set to an empty string to disable adding the argument.
         */
        abstract val accessTokenArg: Property<String>

        init {
            assetsMode.convention(ClientAssetsMode.AUTO)
            assetsHashCheck.convention(false)
            addGameDirArg.convention(true)
            addVersionArg.convention(true)
            accessTokenArg.convention("42")
        }
    }
}