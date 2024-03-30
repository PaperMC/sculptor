plugins {
    `kotlin-dsl`
    `kotlin-dsl-precompiled-script-plugins`
}

repositories {
    mavenCentral()
    gradlePluginPortal()
}

dependencies {
    implementation(libs.gradle.shadow)
    implementation(libs.gradle.kotlin.dsl.withVersion(org.gradle.kotlin.dsl.support.expectedKotlinDslPluginsVersion))
    implementation(libs.gradle.plugin.kotlin.withVersion(embeddedKotlinVersion))
    implementation(libs.gradle.plugin.publish)
}

fun Provider<MinimalExternalModuleDependency>.withVersion(version: String): Provider<String> {
    return map { "${it.module.group}:${it.module.name}:$version" }
}

