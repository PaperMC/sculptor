package io.papermc.sculptor.shared.util

/**
 * How Mache should handle the clients extra assets needed, like alternate languages and sounds.
 * By default, {@link #AUTO} is used.
 */
enum class ClientAssetsMode {
    /**
     * This mode tries to find a local copy of the assets directory locally from well known directories of existing launchers
     *
     * if it is unable to find any, it will download the assets from mojang to .gradle/mache/assets
     */
    AUTO,

    /**
     * This mode will always download a copy of the assets from mojang to .gradle/mache/assets
     */
    DOWNLOADED,

    /**
     * This mode will prevent mache from adding any arguments relating to assets,
     * allowing you to specify the assets directory yourself.
     */
    NONE
}