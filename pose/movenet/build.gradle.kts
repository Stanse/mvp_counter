plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "dev.repcounter.pose.movenet"
    compileSdk {
        version =
            release(37) {
                minorApiLevel = 1
            }
    }

    defaultConfig {
        minSdk = 26
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(project(":core:model"))
    implementation(project(":pose:api"))
    // com.google.ai.edge.litert:litert ships as a transitive "TFLite" compat shim and its
    // AndroidManifest claims the same org.tensorflow.lite namespace MediaPipe's own pinned
    // tensorflow-lite-api uses, which the merger rejects when both land in :app. We don't use
    // the LiteRT rebrand API surface, so drop it; revisit when :pose:movenet grows real
    // MoveNet inference in M6.
    implementation(libs.tensorflow.lite) {
        exclude(group = "com.google.ai.edge.litert")
    }
    implementation(libs.tensorflow.lite.gpu) {
        exclude(group = "com.google.ai.edge.litert")
    }
    implementation(libs.kotlinx.coroutines.core)

    testImplementation(libs.junit.jupiter.api)
    testRuntimeOnly(libs.junit.jupiter.engine)
    testRuntimeOnly(libs.junit.platform.launcher)
    testImplementation(libs.truth)
}

tasks.withType<Test> {
    useJUnitPlatform()
}
