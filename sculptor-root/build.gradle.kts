plugins {
    `config-kotlin`
    `config-publish`
}

dependencies {
    shade(projects.sculptorShared)
}

gradlePlugin {
    plugins.all {
        description = "Gradle plugin for the Mache root project"
        implementationClass = "io.papermc.sculptor.root.SculptorRoot"
    }
}
