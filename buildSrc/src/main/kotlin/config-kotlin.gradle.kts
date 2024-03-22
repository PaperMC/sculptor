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
        languageVersion = JavaLanguageVersion.of(17)
    }
    target {
        compilations.configureEach {
            kotlinOptions {
                jvmTarget = "17"
                freeCompilerArgs = listOf("-Xjvm-default=all", "-Xjdk-release=17", "-opt-in=kotlin.io.path.ExperimentalPathApi")
            }
        }
    }
}

repositories {
    maven("https://repo.papermc.io/repository/maven-releases/") {
        name = "PaperMC"
        mavenContent {
            includeGroupAndSubgroups("io.papermc")
        }
    }
    mavenCentral()
}

configurations.all {
    if (name == "compileOnly") {
        return@all
    }
    dependencies.remove(project.dependencies.gradleApi())
    dependencies.removeIf { it.group == "org.jetbrains.kotlin" }
}

dependencies {
    compileOnly(gradleApi())
    compileOnly(kotlin("stdlib-jdk8"))
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
