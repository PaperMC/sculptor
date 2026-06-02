import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    idea
    id("org.gradle.kotlin.kotlin-dsl")
}

java {
    withSourcesJar()
}

tasks.withType(JavaCompile::class).configureEach {
    options.release = 17
}

kotlin {
    jvmToolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
    compilerOptions {
        jvmTarget = JvmTarget.JVM_17
        freeCompilerArgs = listOf("-Xjvm-default=all", "-Xjdk-release=17", "-opt-in=kotlin.io.path.ExperimentalPathApi")
    }
}

repositories {
    mavenCentral {
        mavenContent { releasesOnly() }
    }
    maven("https://repo.papermc.io/repository/maven-releases/") {
        name = "PaperMC"
        mavenContent {
            releasesOnly()
            includeGroupAndSubgroups("io.papermc")
        }
    }
    maven("https://maven.neoforged.net/releases/") {
        name = "Neoforged"
        mavenContent {
            releasesOnly()
            includeGroupAndSubgroups("codechicken")
            includeGroupAndSubgroups("net.covers1624")
        }
    }
}

dependencies {
    compileOnly(gradleApi())
}

configurations.all {
    if (name == "compileOnly") {
        return@all
    }
    dependencies.remove(project.dependencies.gradleApi())
}

tasks.jar {
    manifest {
        attributes(
            "Implementation-Version" to project.version
        )
    }
}

idea {
    module {
        isDownloadSources = true
    }
}
