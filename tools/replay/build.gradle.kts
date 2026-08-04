plugins {
    alias(libs.plugins.kotlin.jvm)
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

    testImplementation(libs.junit.jupiter.api)
    testRuntimeOnly(libs.junit.jupiter.engine)
    testRuntimeOnly(libs.junit.platform.launcher)
    testImplementation(libs.truth)
}

tasks.test {
    useJUnitPlatform()
}
