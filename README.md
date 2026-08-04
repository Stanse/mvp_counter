# RepCounter

Offline, on-device camera rep counter (Android). Full requirements: `REPCOUNTER_ANDROID.md`.
Architecture: `docs/ARCHITECTURE.md`. Non-obvious decisions: `docs/DECISIONS.md`.

Status: **M1 - project skeleton.** Module graph, version catalog, `:core:model` and `:core:dsp`
types, detekt/ktlint, and a launchable-but-empty `:app` are in place. No pose detection, signal
processing, or exercise counting logic yet - that's M2 onward.

## Requirements

- JDK 17
- Android SDK with platform `36.1` **and** `37.1` installed (`sdkmanager "platforms;android-37.1"`),
  plus `local.properties` pointing `sdk.dir` at it (not committed - machine-specific)
- No Android emulator needed for anything except the (not-yet-written) instrumented tests in M6

## Build

```
./gradlew build
```

Runs `assemble` + `test` + `detekt`/`ktlint` + the pure-Kotlin module boundary check
(`verifyModuleBoundaries`) across every module. Everything currently passes without needing any
model file, emulator, or network access at build time.

## Test

```
./gradlew test                     # all JVM unit tests
./gradlew :core:model:test         # single module
```

Levels 2/3 from the spec (replay corpus, instrumented video tests) land in M3/M6 respectively.

## Models

Bundled pose models are **not committed** (`app/src/main/assets/models/*.task`, `*.tflite` are
gitignored). Fetch them with:

```
./scripts/fetch_models.sh
```

This downloads MediaPipe's `pose_landmarker_lite.task` directly. The MoveNet
`movenet_singlepose_lightning_int8.tflite` fallback now lives behind a Kaggle API token (the old
`tfhub.dev` direct-download URLs are gone) - the script prints setup instructions if
`KAGGLE_USERNAME`/`KAGGLE_KEY` aren't set. The app runs fine on MediaPipe alone; MoveNet is an
optional runtime-switchable fallback (spec §12).

## Traces

Not wired up yet (`TraceRecorder` lands in M4, `:tools:replay` corpus runner in M3). This section
will document how to record a session trace on-device and replay it through
`./gradlew :tools:replay:run` once that exists.

## Module map

See `docs/ARCHITECTURE.md` for the full graph. Quick orientation: `:core:*`, `:signals`,
`:analysis:*`, `:pose:api`, `:capture`, `:tools:replay` are plain-JVM Kotlin (no Android SDK,
enforced by a Gradle check); `:pose:mediapipe`, `:pose:movenet`, `:data`, `:feature:workout`,
`:app` are Android modules.
