package io.papermc.sculptor.root

import io.papermc.sculptor.shared.util.DownloadService
import io.papermc.sculptor.root.tasks.CopyVersion
import io.papermc.sculptor.root.tasks.MigrateVersion
import io.papermc.sculptor.root.tasks.OpenVersion
import io.papermc.sculptor.shared.MACHE_DIR
import io.papermc.sculptor.shared.MC_MANIFEST
import io.papermc.sculptor.shared.REPO_URL
import io.papermc.sculptor.shared.util.dotGradleDirectory
import io.papermc.sculptor.shared.util.download
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.file.RegularFile
import org.gradle.api.tasks.Delete
import org.gradle.kotlin.dsl.register
import org.gradle.kotlin.dsl.registerIfAbsent

class SculptorRoot : Plugin<Project> {

    override fun apply(target: Project) {
        target.gradle.sharedServices.registerIfAbsent("download", DownloadService::class) {}

        val mcManifestUrl = "https://piston-meta.mojang.com/mc/game/version_manifest_v2.json"
        val mcManifestFile: RegularFile = target.layout.dotGradleDirectory.file(MC_MANIFEST)
        target.download.download(mcManifestUrl, mcManifestFile)

        target.tasks.register("cleanMacheCache", Delete::class) {
            group = "mache"
            description = "Delete downloaded manifest and jar files from Mojang."
            delete(target.layout.dotGradleDirectory.dir(MACHE_DIR))
        }

        target.tasks.register("open", OpenVersion::class) {
            repoUrl.set(REPO_URL)
        }

        target.tasks.register("migrate", MigrateVersion::class)
        target.tasks.register("copy", CopyVersion::class)
    }
}
