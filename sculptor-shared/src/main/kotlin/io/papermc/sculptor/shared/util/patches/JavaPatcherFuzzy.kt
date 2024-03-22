package io.papermc.sculptor.shared.util.patches

import com.github.difflib.patch.Patch
import io.papermc.sculptor.shared.util.patches.JavaPatcher

class JavaPatcherFuzzy(private val maxFuzz: Int) : JavaPatcher() {

    init {
        if (maxFuzz < 0) {
            throw IllegalArgumentException("max-fuzz argument must be a non-negative integer")
        }
    }

    override fun applyPatch(patch: Patch<String>, lines: List<String>): List<String> {
        return patch.applyFuzzy(lines, maxFuzz)
    }
}
