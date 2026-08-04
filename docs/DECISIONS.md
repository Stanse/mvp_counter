# Decisions

Running log of choices made where `REPCOUNTER_ANDROID.md` was ambiguous, or where the stated
approach didn't survive contact with real tooling. Newest entries at the bottom of each section
they belong to; sections are milestone-ordered.

## M1 — Skeleton

### Library versions

Pinned by querying Maven Central / Google Maven `maven-metadata.xml` directly on 2026-08-04
(training knowledge of 2026 releases is unreliable) and filtering out alpha/beta/RC/dev builds:

| Library | Version | Note |
|---|---|---|
| AGP | 9.3.1 | See "AGP 9 built-in Kotlin" below |
| Kotlin | 2.3.21 | Capped below 2.4.x — see KSP note |
| KSP | 2.3.11 | Latest available; had not published a Kotlin-2.4.x-compatible build yet |
| Compose BOM | 2026.06.01 | |
| CameraX | 1.6.1 | |
| Hilt | 2.60.1 | |
| Room | 2.8.4 | Room 3.0 (KMP-first) is alpha-only; stayed on the 2.x line |
| kotlinx-serialization | 1.11.0 | |
| kotlinx-coroutines | 1.11.0 | |
| MediaPipe tasks-vision | 1.0.0 | Only published on Google's Maven, not Maven Central |
| TensorFlow Lite (+GPU delegate) | 2.17.0 | |
| JUnit Jupiter | 6.1.2 | |
| Truth | 1.4.5 | |
| Kotest (assertions/runner) | 6.2.3 | |
| Detekt | 1.23.8 | `dev.detekt` 2.0.0 line is alpha-only, stayed on `io.gitlab.arturbosch.detekt` |
| ktlint (via jlleitschuh plugin) | 14.2.0 | wraps ktlint-core 1.8.0 |
| Gradle | 9.6.1 | |
| compileSdk / targetSdk | 37 / 37 (platform 37.1 installed) | See compileSdk note below |

### AGP 9 has built-in Kotlin support — dropped `org.jetbrains.kotlin.android`

AGP 9.0+ compiles Kotlin itself; applying `org.jetbrains.kotlin.android` alongside it is a hard
error ("no longer required... will be removed in AGP 10"). All Android modules apply only
`com.android.application`/`com.android.library` (+ `org.jetbrains.kotlin.plugin.compose` where
Compose is used, + `com.google.devtools.ksp`/Hilt where needed). Pure-JVM modules still apply
`org.jetbrains.kotlin.jvm` as normal — this only affects Android modules.

### compileSdk 37.1, not 36

Originally targeted compileSdk 36 (the only platform installed locally). `androidx.lifecycle`
2.11.0 (pulled in transitively by `activity-compose`/`lifecycle-viewmodel-compose`) requires
compileSdk >= 37 per its AAR metadata check. Rather than pin lifecycle to an older line, bumped
to the actual latest stable platform: `platforms;android-37.1` (installed via `sdkmanager`,
downloaded a `cmdline-tools` package for this since the SDK only shipped with 29/35/36.1).
`compileSdk` uses AGP's minor-API DSL (`compileSdk { version = release(37) { minorApiLevel = 1 } }`)
since only `android-37.1`, not plain `android-37`, is installed. `targetSdk = 37` to match.

### `SignalId` and `LandmarkSchema` live in `:core:model`, not `:signals`/`:pose:api`

§4/§5 of the spec list `SignalId` under `:signals` and imply `LandmarkSchema` is a `:pose:api`
concern, but `:core:model`'s own contract (§6) has `SignalFrame(values: Map<SignalId, Float>)`
and `PoseDetector.landmarkSchema: LandmarkSchema` — both types are referenced *by* `:core:model`
and `:pose:api`'s stated contracts, so they must be defined at or below that layer to avoid a
dependency cycle. Put both in `:core:model` alongside `PoseFrame`/`Landmark`, which they're
structurally part of anyway (a schema is just "what `PoseFrame.landmarks` indices mean").

### `FrameImage` lives in `:core:model`, `:capture` and `:pose:api` are pure-Kotlin

`FrameImage` is needed by both `:capture`'s `FrameSource` and `:pose:api`'s `PoseDetector`
contracts. Defined it as a plain Android-agnostic data class (width/height/rotation/format/bytes,
no `ImageProxy`/`Bitmap`) in `:core:model` so neither module needs to depend on the other for it.

This let `:capture` and `:pose:api` themselves be **pure-Kotlin JVM modules**, even though §5's
table doesn't tag them `[pure Kotlin]`. Chose the more-testable option per the doc's own
tie-breaking rule (§0: "if ambiguous, pick whichever is easier to test"):
`TraceFrameSource` (`:capture`) has to run in Level-2 replay tests "without Android, without a
model, without an emulator" (§10) — that's only possible if `:capture` itself has no Android
dependency. `PoseDetector` the *interface* (`:pose:api`) needs nothing Android-specific either;
only its concrete implementations (`:pose:mediapipe`, `:pose:movenet`) touch the real SDK/GPU
delegate, so those two stay Android library modules. `CameraFrameSource` and
`VideoFileFrameSource` (the implementations that *do* need CameraX/MediaCodec) will be added to
`:app` when M4/M6 build them, rather than to `:capture`.

### `:pose:api`, `:tools:replay` added to the pure-Kotlin dependency guard

Beyond the six modules §5 explicitly tags `[pure Kotlin]`, the guard in the root `build.gradle.kts`
also covers `:pose:api` and `:capture` (see above) and `:tools:replay` (the JVM CLI, which by
construction can't depend on anything Android). Enforced via a `verifyNoAndroidDependencies`
task per pure module that resolves `compileClasspath`/`runtimeClasspath` and fails the build if
any `androidx.*`/`com.android.*` artifact shows up, wired into `check`. `androidx.annotation`'s
JVM variant (`androidx.annotation:annotation-jvm`, not the `annotation` coordinate one would
guess) is allow-listed: it's a zero-dependency annotations-only jar with no Android SDK
dependency, and several pure-Kotlin/KMP libraries pull it in transitively.

Implementation note: the check must resolve dependencies and build its `List<String>` of
offenders at **task-configuration time**, then only reference that plain list inside `doLast`.
Building it inside `doLast` from `configurations.matching{}` captures a live
`Configuration`/`Project` reference in the task's up-to-date-checking state, which the
configuration cache rejects (`LifecycleAwareProject` "not supported" error).

### JUnit 5, not JUnit 4

The spec allows JUnit4 "if simpler with AGP." Pure-Kotlin JVM modules don't have AGP's JUnit4
assumptions to work around, and Kotest/Truth both integrate cleanly with JUnit 5's platform, so
went with JUnit 5 (Jupiter) everywhere. Requires an explicit
`testRuntimeOnly(libs.junit.platform.launcher)` alongside `junit-jupiter-engine` — Gradle 9's
`useJUnitPlatform()` doesn't pull the platform launcher in automatically the way IDE run
configurations do; omitting it fails every test task with "Failed to load JUnit Platform."

### `:core:dsp` in M1 has interface signatures only

Per M1's acceptance criteria ("`:core:model` and `:core:dsp` with types"), `:core:dsp` got
`TimedSample`, `FloatRingBuffer` (a real, tested implementation — it's a data structure, not an
algorithm), and interface contracts for `Resampler`/`StreamingFilter`/`PeakDetector`/
`CadenceEstimator`/`CrossCorrelator` with no bodies. Real implementations (Butterworth/One-Euro
filters, autocorrelation, etc.) are M2 work, test-first as §14 requires.

### `:analysis:api` contracts deferred to M3

Left `:analysis:api` as an empty module (build file only) in M1 rather than pre-writing
`ExerciseDescriptor`/`ExerciseAnalyzer` now. M1's acceptance criteria only names `:core:model`
and `:core:dsp` as needing types; §13 assigns the analysis-layer contracts to M3 alongside the
signal layer they're designed against, and speculatively filling them in now risks a shape that
doesn't fit `:signals`' actual output.

### TensorFlow Lite / LiteRT namespace collision

`org.tensorflow:tensorflow-lite:2.17.0` transitively pulls `com.google.ai.edge.litert:litert`
(Google's TFLite-compat rebrand), whose `AndroidManifest.xml` claims the `org.tensorflow.lite`
namespace — the same namespace MediaPipe's own pinned `tensorflow-lite-api:2.13.0` uses. AGP 9's
manifest merger now hard-fails on duplicate namespaces once both land in `:app`'s graph.
Excluded `com.google.ai.edge.litert` from `:pose:movenet`'s `tensorflow-lite`/`tensorflow-lite-gpu`
dependencies; also dropped `tensorflow-lite-support` entirely since M1 doesn't use it and it has
its own self-referential namespace clash (`tensorflow-lite-support` vs
`tensorflow-lite-support-api`, same version, same namespace). Revisit when `:pose:movenet` grows
real inference code in M6 — may need `resolutionStrategy` pinning instead of a blanket exclude.

### Known upstream warning: detekt 1.23.8 vs Gradle 9.6

`apply(plugin = "io.gitlab.arturbosch.detekt")` triggers a "will be removed in Gradle 10"
deprecation warning (`ReportingExtension.file(String)`) — it's inside the detekt plugin's own
`apply()` implementation, not fixable from a consumer build script. Harmless for now; watch for
a detekt patch release before Gradle 10.

### Configuration cache stays on

`org.gradle.configuration-cache=true` in `gradle.properties`. The only friction it caused (the
module-boundary guard task) has a documented fix above; keeping it on since Gradle is moving
toward requiring it eventually and it's a meaningful CI speedup.
