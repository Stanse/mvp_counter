import io.gitlab.arturbosch.detekt.extensions.DetektExtension
import org.jlleitschuh.gradle.ktlint.KtlintExtension

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt) apply false
    alias(libs.plugins.detekt) apply false
    alias(libs.plugins.ktlint) apply false
}

// Modules whose sources must never depend on the Android SDK: this is the ~80% of the
// codebase (DSP, signal processing, exercise analyzers) that is unit-tested on the plain
// JVM in CI without an emulator. See docs/ARCHITECTURE.md.
val pureKotlinModules = setOf(
    ":core:model",
    ":core:dsp",
    ":signals",
    ":analysis:api",
    ":analysis:jumprope",
    ":analysis:strength",
    ":pose:api",
    ":capture",
    ":tools:replay",
)

subprojects {
    apply(plugin = "io.gitlab.arturbosch.detekt")
    apply(plugin = "org.jlleitschuh.gradle.ktlint")

    extensions.configure<DetektExtension> {
        buildUponDefaultConfig = true
        allRules = false
        config.setFrom(files("$rootDir/config/detekt/detekt.yml"))
        source.setFrom(files("src/main/kotlin", "src/main/java"))
    }

    extensions.configure<KtlintExtension> {
        version.set("1.8.0")
    }

    if (path in pureKotlinModules) {
        plugins.withId("com.android.base") {
            throw GradleException(
                "Module $path is declared pure-Kotlin in the root build script but has an " +
                    "Android plugin applied. Remove the Android plugin or drop it from " +
                    "pureKotlinModules.",
            )
        }

        val verifyNoAndroidDependencies = tasks.register("verifyNoAndroidDependencies") {
            group = "verification"
            description = "Fails if $path resolves any androidx.*/com.android.* runtime dependency."

            // Resolved here, at task-configuration time, into a plain List<String> - not inside
            // doLast - because a live Configuration/Project reference can't be captured by a
            // doLast closure under the configuration cache (see docs:
            // configuration_cache_requirements.html#config_cache:requirements:disallowed_types).
            val forbiddenGroups = listOf("androidx", "com.android")
            // androidx.annotation is a zero-dependency, Android-SDK-free jar (plain
            // @interface definitions) that several pure-Kotlin/multiplatform libraries pull in
            // transitively; it does not compromise JVM-only testability.
            // Resolves to the "-jvm" variant artifact, not the multiplatform "annotation"
            // coordinate one might expect - verified empirically, see docs/DECISIONS.md.
            val allowedArtifacts = setOf("androidx.annotation:annotation-jvm")

            val offenders: List<String> = configurations
                .matching { it.isCanBeResolved && (it.name == "runtimeClasspath" || it.name == "compileClasspath") }
                .flatMap { config ->
                    config.incoming.artifacts.artifacts.mapNotNull { artifact ->
                        val id = artifact.id.componentIdentifier.toString()
                        val group = id.substringBefore(':')
                        if (forbiddenGroups.any { group == it || group.startsWith("$it.") } &&
                            id !in allowedArtifacts
                        ) {
                            "${config.name}: $id"
                        } else {
                            null
                        }
                    }
                }

            doLast {
                if (offenders.isNotEmpty()) {
                    throw GradleException(
                        "Module $path is declared pure-Kotlin but resolves Android SDK " +
                            "dependencies:\n" + offenders.joinToString("\n") { "  - $it" },
                    )
                }
            }
        }

        afterEvaluate {
            tasks.named("check") {
                dependsOn(verifyNoAndroidDependencies)
            }
        }
    }
}

tasks.register("verifyModuleBoundaries") {
    group = "verification"
    description = "Verifies pure-Kotlin modules have no Android SDK dependency (see subprojects block)."
    dependsOn(pureKotlinModules.map { "$it:verifyNoAndroidDependencies" })
}
