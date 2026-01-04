plugins {
    kotlin("jvm") version "1.9.0"
    application
    id("org.jlleitschuh.gradle.ktlint") version "12.1.0"
}

group = "com.example"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    implementation(kotlin("stdlib"))
    implementation("build.skir:skir-client:0.1.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
    implementation("com.squareup.okio:okio:3.6.0")
}

kotlin {
    jvmToolchain(17)
}

application {
    mainClass.set(
        if (project.hasProperty("mainClass")) {
            project.property("mainClass") as String
        } else {
            "examples.SnippetsKt"
        },
    )
}
