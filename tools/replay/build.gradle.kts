plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    application
}

kotlin {
    jvmToolchain(17)
}

application {
    mainClass.set("dev.repcounter.tools.replay.MainKt")
}

dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:dsp"))
    implementation(project(":signals"))
    implementation(project(":analysis:api"))
    implementation(project(":analysis:jumprope"))
    implementation(project(":analysis:strength"))
    implementation(project(":capture"))
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.core)

    testImplementation(libs.junit.jupiter.api)
    testImplementation(libs.junit.jupiter.params)
    testRuntimeOnly(libs.junit.jupiter.engine)
    testRuntimeOnly(libs.junit.platform.launcher)
    testImplementation(libs.truth)
    testImplementation(libs.kotlinx.coroutines.test)
}

tasks.test {
    useJUnitPlatform()
}
