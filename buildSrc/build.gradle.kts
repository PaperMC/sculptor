import org.gradle.kotlin.dsl.support.expectedKotlinDslPluginsVersion

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
    implementation(libs.gradle.plugin.publish)
    implementation(libs.gradle.kotlin.jvm.withVersion(embeddedKotlinVersion))
    implementation(libs.gradle.kotlin.dsl.withVersion(expectedKotlinDslPluginsVersion))
}

fun Provider<MinimalExternalModuleDependency>.withVersion(version: String): Provider<String> {
    return map { "${it.module.group}:${it.module.name}:$version" }
}
