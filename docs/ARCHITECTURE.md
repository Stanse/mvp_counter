# Architecture

See `REPCOUNTER_ANDROID.md` for the full spec this implements. This file tracks the module
graph as actually built, which differs slightly from the spec's own module table — see
`docs/DECISIONS.md` for why.

## Pipeline

```
Camera/video/trace frame
    -> PoseDetector.detect()        (:pose:mediapipe | :pose:movenet)   [not wired up until M4]
    -> PoseFrame
    -> PoseNormalizer                (:signals - scale by torso length, hold missing landmarks)
    -> SignalExtractor                (:signals, via LandmarkSchema)
    -> SignalFrame (irregular dt)
    -> Resampler                      (:core:dsp, fixed 50 Hz grid - pivoted per-signal by
                                       :tools:replay's SignalResampling, see docs/DECISIONS.md)
    -> ExerciseAnalyzer.process()    (:analysis:jumprope | :analysis:strength - each owns its
                                       own detrend/bandpass, no separate FilterBank stage)
    -> AnalyzerEvent
    -> SessionAggregator              (:data / :feature:workout)   [not built until M5]
    -> UI + Room + tone
```

`ExerciseAnalyzer` implementations never see a `Landmark` or a `FrameImage` - only
`SignalFrame`s in, `AnalyzerEvent`s out. That boundary is what makes the DSP and analysis layers
plain-JVM-testable without a camera, a model, or an emulator. `:tools:replay`'s `ReplayPipeline`
(main sources) wires `PoseFrame -> ... -> AnalyzerEvent` end to end over an already-collected
`List<PoseFrame>` - the same shape a live, `Flow`-based capture pipeline will drive in M4/M5, but
synchronous since a trace has no camera clock to keep up with.

## Module graph

```
:core:model   [pure Kotlin] PoseFrame, Landmark, FrameImage, LandmarkSchema, SignalFrame,
                             SignalId, AnalyzerEvent, QualityKind - depends on nothing in-repo
:core:dsp     [pure Kotlin] LinearResampler, ButterworthBandpassFilter, OneEuroFilter,
                             HysteresisPeakDetector, AutocorrelationCadenceEstimator,
                             NormalizedCrossCorrelator, FloatRingBuffer (M2, implemented) -
                             depends on nothing in-repo

:pose:api        [pure Kotlin] PoseDetector, FrameImage-consuming contract -> :core:model
:pose:mediapipe  [Android]     MediaPipe Tasks Vision impl -> :pose:api, :core:model
:pose:movenet    [Android]     TFLite MoveNet impl -> :pose:api, :core:model

:signals      [pure Kotlin] ScaleOnlyPoseNormalizer, SignalExtractor (M3, implemented) ->
                             :core:model, :core:dsp

:analysis:api        [pure Kotlin] ExerciseAnalyzer/Descriptor/Factory, Gate, ThresholdAnalyzer
                                    (the declarative FSM archetype itself - see
                                    docs/DECISIONS.md) -> :core:model
:analysis:jumprope   [pure Kotlin] JumpRopeAnalyzer, CrossCorrelationTechniqueClassifier
                                    (M3, implemented) -> :analysis:api, :core:model, :core:dsp
:analysis:strength   [pure Kotlin] StrengthExercises (squat/pushup ThresholdAnalyzerConfigs,
                                    M3, implemented) -> :analysis:api, :core:model

:capture      [pure Kotlin] FrameSource (for CameraFrameSource/VideoFileFrameSource, which need
              CameraX/MediaCodec and live in :app), PoseFrameSource + TraceFrameSource + the
              .jsonl.gz trace format (M3, implemented) -> :core:model

:data             [Android] Room, repositories, TraceRecorder -> :core:model
:feature:workout  [Android] Compose screens, skeleton overlay, ViewModel
                             -> :core:model, :analysis:api, :capture, :data

:tools:replay [pure Kotlin, application] ReplayPipeline, SignalResampling, CorpusRunner +
              CorpusMetrics (precision/recall/F1 via +/-150ms window matching, spec §10), CLI
              corpus runner printing CSV (M3, implemented) -> :core:model, :core:dsp, :signals,
              :analysis:api, :analysis:jumprope, :analysis:strength, :capture

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
