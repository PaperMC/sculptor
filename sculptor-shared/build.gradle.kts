plugins {
    `config-kotlin`
    kotlin("plugin.serialization") version "2.2.21"
}

dependencies {
    api(files(libs.javaClass.superclass.protectionDomain.codeSource.location))

    api(libs.coroutines)
    api(libs.diffpatch)
    api(libs.jgit)

    api(libs.serialize.core)
    api(libs.serialize.json)
    implementation(libs.serialize.core)
    implementation(libs.serialize.json)

    api(libs.xml.core)
    api(libs.xml.serialize) {
        isTransitive = false
    }
}

gradlePlugin {
    plugins.all {
        description = "Gradle plugin for the Mache root project"
        implementationClass = "io.papermc.sculptor.root.SculptorRoot"
    }
}
