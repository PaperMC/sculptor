plugins {
    `config-kotlin`
    `config-publish`
}

dependencies {
    shade(projects.sculptorShared)
}

gradlePlugin {
    plugins.all {
        description = "Gradle plugin for Mache version projects"
        implementationClass = "io.papermc.sculptor.version.SculptorVersion"
    }
}
