# Architecture

See `REPCOUNTER_ANDROID.md` for the full spec this implements. This file tracks the module
graph as actually built, which differs slightly from the spec's own module table — see
`docs/DECISIONS.md` for why.

## Pipeline

```
Camera/video/trace frame
    -> PoseDetector.detect()        (:pose:mediapipe | :pose:movenet)
    -> PoseFrame
    -> PoseNormalizer                (:signals)
    -> SignalExtractor                (:signals, via LandmarkSchema)
    -> SignalFrame (irregular dt)
    -> Resampler -> FilterBank        (:core:dsp, fixed 50 Hz grid)
    -> ExerciseAnalyzer.process()    (:analysis:jumprope | :analysis:strength)
    -> AnalyzerEvent
    -> SessionAggregator              (:data / :feature:workout)
    -> UI + Room + tone
```

`ExerciseAnalyzer` implementations never see a `Landmark` or a `FrameImage` - only
`SignalFrame`s in, `AnalyzerEvent`s out. That boundary is what makes the DSP and analysis layers
plain-JVM-testable without a camera, a model, or an emulator.

## Module graph

```
:core:model   [pure Kotlin] PoseFrame, Landmark, FrameImage, LandmarkSchema, SignalFrame,
                             SignalId, AnalyzerEvent, QualityKind - depends on nothing in-repo
:core:dsp     [pure Kotlin] Resampler, StreamingFilter, PeakDetector, CadenceEstimator,
                             CrossCorrelator, FloatRingBuffer - depends on nothing in-repo

:pose:api        [pure Kotlin] PoseDetector, FrameImage-consuming contract -> :core:model
:pose:mediapipe  [Android]     MediaPipe Tasks Vision impl -> :pose:api, :core:model
:pose:movenet    [Android]     TFLite MoveNet impl -> :pose:api, :core:model

:signals      [pure Kotlin] PoseNormalizer, SignalExtractor -> :core:model, :core:dsp

:analysis:api        [pure Kotlin] ExerciseAnalyzer/Descriptor/Registry -> :core:model
:analysis:jumprope   [pure Kotlin] -> :analysis:api, :core:model, :core:dsp
:analysis:strength   [pure Kotlin] -> :analysis:api, :core:model

:capture      [pure Kotlin] FrameSource, TraceFrameSource -> :core:model
              (CameraFrameSource/VideoFileFrameSource, which need CameraX/MediaCodec, live in
              :app - see docs/DECISIONS.md)

:data             [Android] Room, repositories, TraceRecorder -> :core:model
:feature:workout  [Android] Compose screens, skeleton overlay, ViewModel
                             -> :core:model, :analysis:api, :capture, :data

:tools:replay [pure Kotlin, application] CLI corpus runner
              -> :core:model, :core:dsp, :signals, :analysis:api, :analysis:jumprope,
                 :analysis:strength, :capture

:app  [Android application] DI graph, navigation, CameraFrameSource, wires everything together
```

Nine modules (`:core:model`, `:core:dsp`, `:signals`, `:analysis:api`, `:analysis:jumprope`,
`:analysis:strength`, `:pose:api`, `:capture`, `:tools:replay`) are enforced pure-Kotlin by a
Gradle task (`verifyNoAndroidDependencies`, wired into `check`) that fails the build if any of
them resolves an `androidx.*`/`com.android.*` dependency. That's the ~80% of the codebase CI
tests without an emulator or a model file.

## Adding an exercise

Not written yet - lands in M6 as `docs/ADDING_EXERCISE.md`, proven by adding jumping jacks as a
fourth exercise touching only a new module/config entry.
