package io.papermc.sculptor.version.tasks

import codechicken.diffpatch.util.PatchMode
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.UntrackedTask
import org.gradle.api.tasks.options.Option

@UntrackedTask(because = "Always apply patches")
abstract class ApplyPatchesFuzzy : ApplyPatches() {

    @get:Input
    @get:Option(
        option = "min-fuzz",
        description = "Min fuzz. The minimum quality needed for a patch to be applied. Default is 0.5.",
    )
    abstract val minFuzz: Property<String>

    override fun mode(): PatchMode {
        return PatchMode.FUZZY
    }

    override fun minFuzz(): Float {
        return minFuzz.get().toFloat()
    }
}
