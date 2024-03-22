import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

plugins {
    id("org.jetbrains.kotlin.jvm")
    id("com.github.johnrengelman.shadow")
    id("com.gradle.plugin-publish")
}

fun version(): String = version.toString()

val shade: Configuration by configurations.creating
configurations.implementation {
    extendsFrom(shade)
}

fun ShadowJar.configureStandard() {
    configurations = listOf(shade)

    dependencies {
        exclude(dependency("org.jetbrains.kotlin:.*:.*"))
    }

    exclude("META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA", "OSGI-INF/**", "*.profile", "module-info.class", "ant_tasks/**")

    mergeServiceFiles()
}

val sourcesJar by tasks.existing(AbstractArchiveTask::class) {
    from(
        zipTree(project(":sculptor-shared").tasks
            .named("sourcesJar", AbstractArchiveTask::class)
            .flatMap { it.archiveFile })
    ) {
        exclude("META-INF/**")
    }
}

val prefix = project.name.substringAfter("sculptor-")

gradlePlugin {
    website.set("https://github.com/PaperMC/sculptor")
    vcsUrl.set("https://github.com/PaperMC/sculptor")
    plugins.create("sculptor-$prefix") {
        id = "io.papermc.sculptor." + prefix
        displayName = "sculptor $prefix"
        tags.set(listOf("paper", "minecraft"))
    }
}

val shadowJar by tasks.existing(ShadowJar::class) {
    archiveClassifier.set(null as String?)
    configureStandard()
}

publishing {
    repositories {
        maven("https://repo.papermc.io/repository/maven-snapshots/") {
            credentials(PasswordCredentials::class)
            name = "paper"
        }
    }

    publications {
        withType(MavenPublication::class).configureEach {
            pom {
                pomConfig()
            }
        }
    }
}

fun MavenPom.pomConfig() {
    val repoPath = "PaperMC/sculptor"
    val repoUrl = "https://github.com/$repoPath"

    name.set("sculptor")
    description.set("Gradle plugin for the PaperMC project")
    url.set(repoUrl)
    inceptionYear.set("2024")

    licenses {
        license {
            name.set("MIT")
            url.set("$repoUrl/blob/master/LICENSE")
            distribution.set("repo")
        }
    }

    issueManagement {
        system.set("GitHub")
        url.set("$repoUrl/issues")
    }

    scm {
        url.set(repoUrl)
        connection.set("scm:git:$repoUrl.git")
        developerConnection.set("scm:git:git@github.com:$repoPath.git")
    }
}
