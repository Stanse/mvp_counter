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

## M2 — DSP core and synthetic tests

### Butterworth bandpass built as a highpass+lowpass cascade, not a single bandpass biquad

§7 asks for "Butterworth bandpass 0.7-6 Hz". A textbook single bandpass biquad (RBJ cookpage
constant-skirt-gain form) exists, but for a wide, multi-octave band like this one, cascading a
2nd-order Butterworth highpass (cutoff = `lowHz`) with a 2nd-order Butterworth lowpass
(cutoff = `highHz`) is the simpler, more standard construction and lets each section be reasoned
about (and tested) independently. `ButterworthDesign`/`Biquad` in `:core:dsp` implement the RBJ
audio-EQ-cookbook formulas directly rather than pulling in a DSP library, since the app has no
network access to fetch one transitively-verified and the formulas are ~15 lines.

### `PeakDetector`/`CadenceEstimator` know nothing about jump-rope-specific refractory tuning

Spec §8.1 step 3 says the refractory period is `0.6 / f0` (from the cadence estimate). `:core:dsp`
is domain-agnostic (see `TimedSample`'s KDoc), so `HysteresisPeakDetector` takes a plain
`minRefractoryMs: Long` instead of a `CadenceEstimator` reference - wiring "refractory derived
from cadence" is `:analysis:jumprope`'s job (M3), not this layer's. Same reasoning for
`AutocorrelationCadenceEstimator`'s `minHz`/`maxHz` search bounds: generic constructor
parameters here, exercise-specific defaults get chosen by the caller in M3.

### `HysteresisPeakDetector` defaults: `k=1.0`, hysteresis ratio `0.3`, not `k=1.5`

A first pass used `k=1.5`, which is mathematically unreachable for a pure sine: peak amplitude is
only `sqrt(2) * RMS ≈ 1.414 * RMS`, so `1.5 * RMS` never triggers. Settled on `k=1.0` /
`lowThreshold = 0.3 * highThreshold` - low enough to trigger reliably before the peak, low enough
on the way down to close the cycle near the zero crossing, high enough above typical
sensor/synthetic noise floors to avoid double-triggering. Real jump-rope-signal tuning is M3 work
against real/synthetic traces, same as the refractory period above.

### `:core:dsp`'s "synthetic signal generator" lives in `src/test`, not `src/main`

§13's M2 bullet ("генератор синтетических сигналов") is satisfied by
`core/dsp/src/test/kotlin/.../synthetic/SyntheticSignals.kt` (seeded sine/noise/drop helpers),
used only by `:core:dsp`'s own Level-1 tests. This is deliberately not shared via
`java-test-fixtures` yet - M3's trace-and-golden-file generator (§9) is a different, higher-level
concept (whole `PoseFrame` sequences with technique segments, not scalar signals) that belongs to
`:tools:replay`'s test infra when that's built, not a reuse of this one.

### Level-1 test list from §10 is split across M2 and M3, not all delivered now

§10 groups "FSM `ThresholdAnalyzer`: дребезг" and "Гейт активности" under Level 1, but both
target components that don't exist until M3 (`:analysis:strength`, `:analysis:jumprope`'s
activity gate). M2 delivers the Level-1 tests for the components M2 actually builds: `Resampler`,
the two `StreamingFilter`s, `PeakDetector`, `CadenceEstimator`, `CrossCorrelator`. The remaining
Level-1 tests land in M3 alongside the analyzers they test.

### Cross-correlation lag sign convention, and its inherent half-period ambiguity

`NormalizedCrossCorrelator.correlate(a, b, ...)`: positive `lagMs` means `b` is delayed relative
to `a` (`b[i+lag]` paired with `a[i]`, best match when `b(t) = a(t - lag)`). Worth recording
because it's easy to get backwards and there's nothing in the type system to catch a sign flip.

Also: for a technique-classification use (`ANKLE_Y_L` vs `ANKLE_Y_R`, spec §8.1 step 5), a shift
of exactly half the signal's period is mathematically indistinguishable from a shift of *minus*
half the period - both describe the same alternating relationship. `NormalizedCrossCorrelatorTest`
asserts on `abs(lagMs)` for that case rather than a signed value; `TechniqueClassifier` in M3
should do the same (classify `ALTERNATING` on `|lag| ≈ T/2`, not a specific sign).

## M3 — Signals and analyzers

### `PoseNormalizer` doesn't translate the hip to `(0, 0)`, only scales by torso length

§7's pipeline diagram says "центр таза → 0, масштаб по длине торса". Implemented as
scale-only: `ScaleOnlyPoseNormalizer` divides every landmark coordinate by torso length but
never subtracts the hip center. Translating every frame to its *own* hip center would make
`HIP_Y` identically zero on every frame (each frame's hip minus itself), which destroys the
exact oscillation §8.1's `JumpRopeAnalyzer` needs to detrend and count. §8.1's own worked
formula - `HIP_Y` "среднее по бёдрам, нормализовано на длину торса" - only mentions scale, which
is the tell that §7's diagram is a slight simplification, not a literal spec. Translation also
isn't needed for any declared `SignalId`: every angle (`KNEE_ANGLE_*`, `ELBOW_ANGLE_*`,
`TORSO_TILT`) is translation-invariant on its own. Missing/low-visibility landmarks
(`visibility < 0.5`, placeholder threshold) hold their last known-good raw position rather than
being zeroed or interpolated - simplest option to reason about and test, per §0's tie-break rule.

### `TORSO_TILT` is degrees from horizontal, not from vertical

`0°` = lying flat, `90°` = standing upright. Chosen to match §8.2's own pushup example,
`Gate.TorsoHorizontal(maxTiltDeg = 35f)`: a pushup's torso is *supposed* to read near `0°`
(mostly horizontal, some sag/pike tolerated up to 35°) and a standing arm-bend should read near
`90°` and fail the gate. Tilt-from-*vertical* would invert that relationship and make the gate's
own name backwards.

### `ThresholdAnalyzer` and `Gate` live in `:analysis:api`, not `:analysis:strength`

§4 calls `ThresholdAnalyzer` one of two reusable *archetypes* (the other being the periodic one
`JumpRopeAnalyzer` implements directly - see below), and §8.2's whole point is that a new
exercise of this shape is "one config, zero code." Putting the FSM itself in `:analysis:api`
alongside `ExerciseAnalyzer`/`ExerciseDescriptor` is what makes `:analysis:strength`'s
contribution *actually* just a config list (`StrengthExercises.kt`) - if the FSM lived in
`:analysis:strength`, `:analysis:api` would have declared an archetype it doesn't provide.

### No `PeriodicAnalyzer` archetype extraction yet - `JumpRopeAnalyzer` implements `ExerciseAnalyzer` directly

§4 names `PeriodicAnalyzer` as the second archetype (fast cyclic exercises). M3 only has one
periodic exercise to generalize from; extracting a shared base now would be guessing its shape
from a single example. Deferred to M6, when jumping jacks (the spec's own extensibility proof)
either reuses this machinery directly or reveals what's actually common between two periodic
exercises - premature abstraction from n=1 tends to guess wrong.

### `JumpRopeAnalyzer` composes `:core:dsp` directly instead of a shared `FilterBank` stage

§7's diagram puts a `FilterBank` (Butterworth bandpass + One-Euro) between `Resampler` and
`ExerciseAnalyzer`, but §6 doesn't define a `FilterBank` contract, and §8.1 assigns this specific
exercise its own bandpass parameters (`0.8-6 Hz`, not §7's generic `0.7-6 Hz`). `JumpRopeAnalyzer`
owns a `ButterworthBandpassFilter`, `AutocorrelationCadenceEstimator` and `HysteresisPeakDetector`
internally rather than assuming a pipeline stage already filtered its input. Consequence: it
expects `SignalFrame`s already on a fixed-rate grid (post-`Resampler`) but does its own signal
conditioning from there - documented on the class itself.

### `HysteresisPeakDetector.minRefractoryMs` became a mutable `var`

§8.1 step 3 wants the refractory period re-derived from the live cadence estimate every update
(`0.6 / f0`), but `:core:dsp` (M2) shipped it as a constructor-only `val`, and `:core:dsp` itself
was deliberately kept ignorant of cadence (see the M2 entry above). Rather than reconstruct a new
detector on every cadence update - which would throw away its RMS-window state - `minRefractoryMs`
became a mutable property `JumpRopeAnalyzer` writes to after each `CadenceEstimator.update()`.
This is exactly the "caller computes it, `:core:dsp` just accepts it" split the M2 KDoc already
promised, just made concrete now that there's a caller.

### Activity gate: "3 valid cycles" = 4 peaks, 3 measured periods

§8.1 step 4: counting doesn't start until "3 подряд валидных цикла со стабильным периодом", then
those 3 cycles backfill. A cycle's *period* needs two peaks to measure, so 3 period measurements
need 4 consecutive peaks (`p0..p3`, periods `p1-p0`, `p2-p1`, `p3-p2`). `p0` only marks where the
first measured period starts - it has no period of its own - so the 3 *backfilled* reps are
`p1, p2, p3`, and the peak stream continues normally (one rep per peak) from `p4` on. This is a
judgment call on a genuinely ambiguous count (§0's tie-break: picked the reading that's an exact,
testable number rather than an approximate one) - `JumpRopeAnalyzerTest` pins down the resulting
contiguous-index, batch-backfill behavior directly.

### `MIN_CYCLE_AMPLITUDE` / `MIN_ACTIVE_AMPLITUDE`: placeholder absolute thresholds, not tuned

§8.1 step 4 also wants walking (0.9 Hz, small amplitude) excluded from counting. The peak
detector's own threshold is *relative* (`k * RMS`), so it self-normalizes to any signal's own
amplitude and would happily count small-amplitude walking on its own terms. Added a second,
*absolute* amplitude floor (torso-length-normalized units) in `JumpRopeAnalyzer` (cycle amplitude)
and `CrossCorrelationTechniqueClassifier` (per-ankle activity) specifically to catch this case.
Values (`0.02`, `0.03`) are placeholders pending real-trace tuning - named constants with
rationale, per §14, so they're easy to find and re-tune later.

### `CrossCorrelationTechniqueClassifier`'s lag search is bounded by its own cadence estimate

First pass used a fixed `maxLagMs` for the cross-correlation search. That reproduced the exact
aliasing bug documented in M2's `NormalizedCrossCorrelator` entry: a bound wide enough to reach a
slow jumper's half period is also wide enough to reach a fast jumper's *full* period, which ties
with lag 0 and can misclassify `BOTH_FEET` as `ALTERNATING` (caught by
`CrossCorrelationTechniqueClassifierTest` before this shipped). Fixed by having the classifier
run its own internal `AutocorrelationCadenceEstimator` on the left ankle and bound the search to
`0.7 *` the estimated period - comfortably past the half period the search exists to find, safely
short of the full period that would alias with it.

### `:capture`'s `TraceFrameSource` implements a new `PoseFrameSource`, not `FrameSource`

§6 lists `TraceFrameSource` as one of three `FrameSource` implementations, but also says it
"отдаёт уже готовые PoseFrame (минуя детектор)" - a recorded trace has no image bytes, only the
landmarks a detector already produced, so it cannot honestly return `Flow<FrameImage>`. Added a
sibling `PoseFrameSource` interface (`fun poseFrames(): Flow<PoseFrame>`) for it. `CameraFrameSource`/
`VideoFileFrameSource` (M4/M6, in `:app`) still implement `FrameSource` as specified.

### Golden files are captured `ReplayPipeline` output, not independently hand-derived

Each `testdata/traces/*.expected.json` is the result of actually running `:tools:replay`'s
pipeline over that trace once (`GenerateFixturesTest`, disabled by default - see its KDoc) and
saving the output, not a value computed by an independent formula. An earlier attempt derived
expected rep timestamps analytically (true sine-peak schedule minus RMS-window warmup minus the
gate's one sacrificed peak) and got close but not exact - filter settling transients and small
resampling-boundary effects shift the real detected count by a cycle or two in ways that are
impractical to hand-predict. Before locking in each golden, `precision`/`mean_offset_ms` from a
real `./gradlew :tools:replay:run` were checked by hand (all three current traces: `precision =
1.0`, `mean_offset_ms ≈ 0` on the clean traces) to confirm every detected rep is a genuine,
well-timed match and not a stored bug - only then is the run's output trustworthy as a *regression*
baseline. This is why `:tools:replay`'s corpus gate (`MAE ≤ 2`, `F1 ≥ 0.95`) currently reads
`MAE = 0`, `microF1 = 1.0`: it's confirming determinism against a checked-in-after-verification
snapshot, not independent ground truth.

While deriving that first analytical attempt, found and fixed a real bug in the generator itself:
`SyntheticTraceGenerator` builds `HIP_Y` as `baseline - amplitude * sin(phase)` (image-space `y`
grows downward, so the jump apex is the *smallest* `y`, at `phase = 90°`), but the peak detector
looks for the filtered signal's *maximum*, which for a negated sine falls at `phase = 270°` -
three quarters through the cycle, not one quarter. The generator's ground-truth peak schedule
now uses `3 * period / 4`.

### CSV output formats floats with `Locale.ROOT`

`TraceMetrics`/`Main` originally used `"%.4f".format(x)`, which uses the JVM's default locale -
on a machine whose locale renders decimals with a comma, this silently produced extra commas
inside CSV fields (`0,9691` where `0.9691` was intended), corrupting the column count. Every
numeric-to-string conversion that feeds the CSV now goes through `String.format(Locale.ROOT, ...)`.

### `PoseFrame.quality` and `AnalyzerEvent.QualityIssue` aren't wired to anything yet

`SignalFrame` (and everything downstream of `SignalExtractor`) has no `quality` field at all, so
nothing in the M3 pipeline can react to a low-confidence frame the way §11's UI eventually needs
to ("мало света", "не видно стоп"). `ReplayRobustnessTest`'s low-quality test only confirms the
pipeline doesn't crash on it, not that it produces a `QualityIssue`. Wiring this up needs a home
(most likely `SessionAggregator` in `:data`/`:feature:workout`, since it's about UI feedback more
than counting logic) and is deferred to M4/M5.
