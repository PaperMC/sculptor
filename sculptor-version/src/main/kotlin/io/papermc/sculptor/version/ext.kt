import org.gradle.kotlin.dsl.DependencyHandlerScope

fun DependencyHandlerScope.codebook(version: String) {
    "codebook"("io.papermc.codebook:codebook-cli:$version:all")
}

fun DependencyHandlerScope.parchment(mcVersion: String, version: String) {
    "paramMappings"("org.parchmentmc.data:parchment-$mcVersion:$version") {
        artifact {
            extension = "zip"
        }
    }
}

fun art(version: String): String = "net.neoforged:AutoRenamingTool:$version:all"
fun vineflower(version: String): String = "org.vineflower:vineflower:$version"
