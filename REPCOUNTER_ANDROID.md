# Задание для Claude Code: Android-приложение офлайн-подсчёта повторений по камере

> Этот файл — единственный источник требований. Читай его целиком перед началом работы.
> Если требование неоднозначно — выбери вариант, который проще тестировать, и запиши решение в `docs/DECISIONS.md`.

---

## 0. Роль

Ты — senior Android-инженер. Строишь **прототип** приложения, которое через камеру телефона считает
повторения упражнений полностью офлайн, на устройстве. Первое упражнение — прыжки на скакалке.
Архитектура обязана быть расширяемой: новое упражнение или новая техника прыжка добавляются
**новым модулем или строкой конфига**, без правок ядра.

Приоритеты в порядке убывания: **тестируемость → расширяемость → точность → красота UI.**

---

## 1. Продуктовая постановка

Пользователь ставит телефон на пол/подставку в 1.5–3 м от себя, выбирает упражнение, жмёт «Старт».
Приложение показывает превью с наложенным скелетом, крупный счётчик, каденс (повторов/мин)
и текущую технику. По завершении — сводка сессии с разбивкой по техникам.

**Ключевые сценарии:**

1. Скакалка, прыжки на двух ногах — счёт прыжков.
2. Скакалка, попеременно левая/правая (boxer step) — счёт + определение техники.
3. Скакалка, на одной ноге — счёт + определение, на какой именно.
4. Смена техники в течение сессии — сегментация: «120 на двух, 80 попеременно, 40 на левой».
5. Приседания и отжимания — счёт (добавляются конфигом, не кодом).

**Явные ограничения продукта (не пытайся решить):**

- Считаются **прыжки тела**, а не проходы скакалки. Double-unders в MVP считаются как один прыжок.
  Отметь это в UI и в README.
- Один человек в кадре.
- Требуется, чтобы в кадр помещалось всё тело (для скакалки — обязательно стопы).

---

## 2. Жёсткие ограничения

| Пункт | Требование |
|---|---|
| Сеть | **Приложение не имеет разрешения `INTERNET`.** В `AndroidManifest.xml` его нет и не будет. Любая библиотека, требующая сети в рантайме, отклоняется |
| Аналитика/крашлитика | Нет |
| Модели | Бандлятся в `assets/`, загружаются локально |
| minSdk | 26 |
| targetSdk / compileSdk | Последний стабильный |
| Язык | Kotlin, без Java-исходников |
| UI | Jetpack Compose |
| Асинхронность | Coroutines + Flow. Никаких RxJava, никаких голых Thread |

---

## 3. Стек

Используй **Gradle version catalog** (`gradle/libs.versions.toml`) и запиши туда точные версии.
Мои знания о версиях могут быть устаревшими — **возьми последние стабильные релизы и убедись, что
проект собирается**, прежде чем идти дальше.

- CameraX (`camera-core`, `camera-camera2`, `camera-lifecycle`, `camera-view`)
- MediaPipe Tasks Vision (`com.google.mediapipe:tasks-vision`) — основной pose-детектор
- TensorFlow Lite + GPU delegate — альтернативный детектор (MoveNet)
- Hilt — DI, включая multibinding для реестра упражнений
- Room — персистентность сессий
- Jetpack Compose + Material 3
- kotlinx-serialization — формат трейсов
- JUnit 5 (или JUnit 4, если проще с AGP) + Truth/Kotest assertions
- Detekt + ktlint в CI

**Модели.** Скачивание `.task`/`.tflite` файлов может быть недоступно из твоего окружения.
Сделай так:

1. Создай `scripts/fetch_models.sh`, который качает `pose_landmarker_lite.task` (MediaPipe) и
   `movenet_singlepose_lightning_int8.tflite` в `app/src/main/assets/models/`.
2. Добавь `assets/models/.gitkeep` и `.gitignore` на сами модели.
3. Приложение при отсутствии модели показывает внятный экран-ошибку со ссылкой на скрипт,
   а не падает.
4. **Все JVM-тесты обязаны проходить без моделей** — они работают на записанных трейсах.

---

## 4. Архитектурный принцип

Между «позой» и «упражнением» стоит промежуточный слой **сигналов** — именованных одномерных
временных рядов, вычисленных из скелета.

```
Кадр → Скелет → Сигналы → Анализатор упражнения → События → Агрегатор → UI
                   ↑
        hipY, kneeAngleL/R, elbowAngleL/R, ankleYL/YR, torsoTilt, ...
```

Следствия, которые нужно соблюдать неукоснительно:

- Анализатор упражнения **не знает про лендмарки, камеру и Android**. Он получает `SignalFrame`
  и возвращает события. Это чистый Kotlin.
- Приседания = FSM по `KNEE_ANGLE_MEAN`. Отжимания = FSM по `ELBOW_ANGLE_MEAN`.
  Скакалка = периодический детектор по `HIP_Y`. Один интерфейс, разные реализации.
- Определение техники прыжка = анализ фазового сдвига между `ANKLE_Y_L` и `ANKLE_Y_R`.
  Новая модель не нужна.

**Два архетипа анализаторов**, оба реализуют `ExerciseAnalyzer`:

- `ThresholdAnalyzer` — дискретные медленные движения (приседания, отжимания, выпады).
  Конфигурируется декларативно.
- `PeriodicAnalyzer` — быстрые циклические (скакалка, jumping jacks, бег на месте).

---

## 5. Структура модулей

```
:app                    DI-граф, навигация, Application, сборка
:core:model             PoseFrame, Landmark, SignalFrame, AnalyzerEvent   [pure Kotlin]
:core:dsp               фильтры, ресемплер, RingBuffer, PeakDetector,
                        оценка каденса, кросс-корреляция                  [pure Kotlin]
:pose:api               interface PoseDetector, FrameImage
:pose:mediapipe         реализация на MediaPipe Tasks Vision
:pose:movenet           реализация на TFLite MoveNet Lightning
:signals                PoseNormalizer, SignalExtractor, SignalId          [pure Kotlin]
:analysis:api           ExerciseAnalyzer, ExerciseDescriptor, Registry     [pure Kotlin]
:analysis:jumprope      JumpRopeAnalyzer + TechniqueClassifier             [pure Kotlin]
:analysis:strength      ThresholdAnalyzer + конфиги squat/pushup           [pure Kotlin]
:capture                CameraFrameSource, VideoFileFrameSource, TraceFrameSource
:data                   Room, репозитории, TraceRecorder
:feature:workout        Compose-экраны, оверлей скелета, ViewModel
:tools:replay           JVM CLI: прогон трейсов, вывод метрик в CSV
```

Модули, помеченные `[pure Kotlin]` — это `kotlin("jvm")`, **без зависимости от Android SDK**.
Это не пожелание, а требование: именно они содержат ~80% логики и тестируются в CI без эмулятора.
Если тебе понадобилось добавить в них `implementation(libs.androidx.*)` — значит абстракция
протекла, останови себя и переделай.

---

## 6. Контракты

Реализуй ровно эти интерфейсы (имена и сигнатуры можешь уточнить, но не смешивай слои).

### `:core:model`

```kotlin
data class Landmark(
    val x: Float, val y: Float, val z: Float,
    val visibility: Float
)

/** Один кадр с распознанной позой. */
data class PoseFrame(
    val tMs: Long,                     // timestamp кадра, НЕ System.currentTimeMillis()
    val landmarks: List<Landmark>,     // нормализованные 0..1, y растёт вниз
    val world: List<Landmark>?,        // метрические, origin в центре таза; null если модель не даёт
    val quality: Float                 // 0..1, агрегат по visibility ключевых точек
)

data class SignalFrame(val tMs: Long, val values: Map<SignalId, Float>)

sealed interface AnalyzerEvent {
    data class Rep(
        val index: Int,
        val tMs: Long,
        val confidence: Float,
        val meta: Map<String, Float> = emptyMap()   // amplitude, durationMs, ...
    ) : AnalyzerEvent

    data class TechniqueChanged(val technique: String, val tMs: Long) : AnalyzerEvent
    data class CadenceUpdated(val hz: Float, val tMs: Long) : AnalyzerEvent
    data class QualityIssue(val kind: QualityKind, val tMs: Long) : AnalyzerEvent
}

enum class QualityKind {
    NO_PERSON, LOW_CONFIDENCE, FEET_OUT_OF_FRAME,
    LOW_FRAMERATE, TOO_FAR, TOO_CLOSE
}
```

### `:pose:api`

```kotlin
interface PoseDetector {
    val id: String
    val landmarkSchema: LandmarkSchema     // BLAZEPOSE_33 | MOVENET_17
    suspend fun detect(image: FrameImage): PoseFrame?
    fun close()
}
```

`LandmarkSchema` нужен, чтобы `SignalExtractor` умел работать с обеими моделями: он обращается
к точкам через семантические имена (`LEFT_HIP`, `RIGHT_ANKLE`), а схема мапит имя на индекс.
Если сигнал невозможно вычислить в данной схеме — экстрактор его просто не кладёт в `SignalFrame`,
а анализатор, которому он нужен, декларирует это в `ExerciseDescriptor.requiredSignals`
и не может быть выбран.

### `:analysis:api`

```kotlin
data class ExerciseDescriptor(
    val id: String,
    val displayName: String,
    val requiredSignals: Set<SignalId>,
    val setupHint: String,                 // "поставь телефон вертикально в 2 м, всё тело в кадре"
    val minFps: Int                        // скакалка: 25; приседания: 12
)

interface ExerciseAnalyzer {
    val descriptor: ExerciseDescriptor
    fun reset()
    fun process(frame: SignalFrame): List<AnalyzerEvent>
}

interface ExerciseAnalyzerFactory {
    val descriptor: ExerciseDescriptor
    fun create(): ExerciseAnalyzer
}
```

Регистрация — через Hilt `@IntoSet`. **Добавление упражнения не должно требовать правок в `:app`.**

```kotlin
@Module @InstallIn(SingletonComponent::class)
abstract class JumpRopeModule {
    @Binds @IntoSet
    abstract fun bindJumpRope(f: JumpRopeAnalyzerFactory): ExerciseAnalyzerFactory
}
```

### `:capture`

```kotlin
interface FrameSource {
    fun frames(): Flow<FrameImage>
}
```

Три реализации: `CameraFrameSource`, `VideoFileFrameSource`, `TraceFrameSource`.
Последняя отдаёт уже готовые `PoseFrame` (минуя детектор) — это основа replay-тестов.

---

## 7. Пайплайн

```
CameraX ImageAnalysis
    ↓ ImageProxy, STRATEGY_KEEP_ONLY_LATEST, 640×480, target FPS 60 (fallback 30)
PoseDetector                        (GPU delegate, отдельный dispatcher)
    ↓ PoseFrame
PoseNormalizer                      центр таза → 0, масштаб по длине торса,
                                    сглаживание пропавших точек
    ↓
SignalExtractor                     чистые функции Skeleton → Float
    ↓ SignalFrame (неравномерный dt)
Resampler                           линейная интерполяция на фиксированные 50 Гц
    ↓
FilterBank                          Butterworth bandpass 0.7–6 Гц (осцилляторные),
                                    One-Euro (угловые)
    ↓
ExerciseAnalyzer                    единственный активный
    ↓ AnalyzerEvent
SessionAggregator                   счётчики, сегменты техник, StateFlow
    ↓
UI + Room + звуковой сигнал
```

**Три вещи, на которых наивные реализации ломаются — реализуй их явно:**

1. **Неравномерный dt.** Кадры дропаются, интервалы плавают. IIR-фильтры и оценка частоты
   требуют равномерной сетки. Ресемплер обязателен и одновременно маскирует потерянные кадры.
2. **Timestamp берётся из `ImageProxy.imageInfo.timestamp`.** Не из настенных часов — иначе
   replay-тесты и обработка видеофайла разойдутся с рантаймом.
3. **Дрейф.** Человек смещается, камера подрагивает. Перед детектором пиков — вычитание
   медленного скользящего среднего (детренд с отсечкой ~0.5 Гц).

---

## 8. Алгоритмы

### 8.1 JumpRopeAnalyzer

Сигналы: `HIP_Y` (среднее по бёдрам, нормализовано на длину торса), `ANKLE_Y_L`, `ANKLE_Y_R`,
`SHOULDER_Y` (контроль качества).

1. **Детренд** `HIP_Y` + bandpass 0.8–6 Гц.
2. **Оценка каденса** — автокорреляция или Goertzel в скользящем окне 3 с → `f0`.
   Она устойчивее прямого счёта пиков и задаёт адаптивные параметры шагу 3.
3. **Детектор пиков** — FSM с гистерезисом. Порог `k · RMS` по бегущему окну,
   refractory period `= 0.6 / f0`. Один цикл (отрыв → приземление) = один `Rep`.
4. **Гейт активности.** Счёт не начинается, пока не набрано 3 подряд валидных цикла со
   стабильным периодом (разброс < 25%). После срабатывания эти 3 цикла **backfill'ятся** в счёт.
   Это убирает ложные срабатывания от разминки, поправки верёвки и ходьбы.
5. **Классификатор техники** — на скользящем окне 2 с считается взаимная корреляция
   `ANKLE_Y_L` и `ANKLE_Y_R`:
   - лаг ≈ 0, амплитуды сопоставимы → `BOTH_FEET`
   - лаг ≈ T/2 → `ALTERNATING`
   - одна амплитуда ≈ 0 и стопа приподнята → `SINGLE_LEFT` / `SINGLE_RIGHT`
   
   При смене класса — `TechniqueChanged`, агрегатор закрывает сегмент.
   Требуется гистерезис по времени (не менее 1.5 с в новом классе), иначе будет дребезг.

`TechniqueClassifier` — **отдельный интерфейс**, эвристика в нём одна из реализаций.
Позже туда подставят обученную модель, поэтому не встраивай эвристику в тело анализатора.

### 8.2 ThresholdAnalyzer

Полностью декларативный, кода на упражнение — ноль:

```kotlin
ThresholdAnalyzer(
    id = "squat",
    displayName = "Приседания",
    signal = SignalId.KNEE_ANGLE_MEAN,
    downBelow = 100f,
    upAbove = 160f,
    minRepMs = 500,
    minAmplitude = 40f,
    gate = Gate.None
)

ThresholdAnalyzer(
    id = "pushup",
    displayName = "Отжимания",
    signal = SignalId.ELBOW_ANGLE_MEAN,
    downBelow = 100f,
    upAbove = 160f,
    minRepMs = 400,
    minAmplitude = 35f,
    gate = Gate.TorsoHorizontal(maxTiltDeg = 35f)
)
```

Конфиги живут в `:analysis:strength` в виде списка, регистрируются циклом.
**Добавление жима/подтягиваний = одна запись в этом списке.**

---

## 9. Трейсы и golden-файлы

### Формат трейса

`.jsonl.gz`, одна строка = один `PoseFrame`, плюс первая строка — заголовок:

```json
{"version":1,"schema":"BLAZEPOSE_33","source":"camera","deviceModel":"...","fps":30.1,"notes":"двойные ноги, 100 прыжков"}
{"tMs":0,"quality":0.93,"lm":[[0.51,0.22,0.0,0.99], ...]}
{"tMs":33,"quality":0.94,"lm":[[0.51,0.23,0.0,0.99], ...]}
```

`TraceRecorder` включается в dev-сборке переключателем в настройках. Трейсы компактны и,
в отличие от видео, не содержат изображения человека — их безопасно коммитить в репозиторий.

### Golden-файл

Рядом с каждым трейсом лежит `<trace>.expected.json`:

```json
{
  "totalReps": 100,
  "tolerance": 2,
  "segments": [
    {"technique": "BOTH_FEET",   "reps": 60, "tolerance": 3},
    {"technique": "ALTERNATING", "reps": 40, "tolerance": 3}
  ],
  "repTimestampsMs": [1203, 1610, 2015, "..."]
}
```

Положи в `testdata/traces/` минимум **три синтетических** трейса, сгенерированных кодом
(генератор — часть тестового кода: синусоида заданной частоты + шум + дропнутые кадры +
переход между техниками). Реальные трейсы Anton добавит позже — опиши процедуру в README.

---

## 10. Требования к тестам

Это самая важная часть задания. Подсчёт повторов — задача детекции, и оценивать её надо как
детекцию, а не бинарным pass/fail.

### Уровень 1 — JVM-юниты на синтетике (быстрые, обязательные)

- `Resampler`: неравномерный вход с дропами → равномерный выход, отсутствие сдвига фазы.
- Butterworth/One-Euro: АЧХ на известных частотах, отсутствие NaN на краях.
- `PeakDetector`: синус 2.5 Гц + белый шум + 10% дропнутых сэмплов → ровно N пиков.
- FSM `ThresholdAnalyzer`: дребезг вокруг порога не должен давать лишних повторов.
- Кросс-корреляция: два синуса с известным лагом → лаг восстанавливается с точностью до сэмпла.
- Гейт активности: первые 3 цикла backfill'ятся; ходьба (0.9 Гц, малая амплитуда) не считается.

### Уровень 2 — replay-тесты на трейсах (основной регресс, в CI)

`TraceFrameSource` → весь сигнальный пайплайн → сравнение с golden.
**Без Android, без модели, без эмулятора, полностью детерминированно.**
Параметризованный тест по всем файлам в `testdata/traces/`.

### Уровень 3 — инструментальные на видеофайлах (nightly)

`VideoFileFrameSource` + реальная модель. Проверяют связку «модель + пайплайн».

### Метрики вместо pass/fail

`:tools:replay` — JVM-таск, прогоняющий весь корпус и печатающий CSV:

```
trace,expected,counted,abs_err,precision,recall,f1,mean_offset_ms
```

- `Rep`-события матчатся с ожидаемыми с окном допуска **±150 мс**.
- Итог: `MAE` по счёту и микро-F1 по корпусу.
- Порог падения CI: MAE > 2.0 или F1 < 0.95 на синтетическом корпусе.

Это даёт видеть «подкрутил фильтр → на трёх трейсах лучше, на одном хуже», а не «упал тест».

### Робастность

Отдельные тесты на: полное отсутствие человека, обрыв потока кадров на 2 с,
`quality` ниже порога, скачок `tMs` назад, дублирующиеся timestamp'ы.
Пайплайн не должен падать и не должен накручивать счётчик.

---

## 11. UI (Compose)

Минимально, но функционально:

- **ExerciseListScreen** — список из `ExerciseRegistry` (не хардкод!), с `setupHint`.
- **WorkoutScreen** — `PreviewView` + Canvas-оверлей скелета, крупный счётчик,
  каденс, бейдж текущей техники, индикатор качества (`QualityIssue` → человекочитаемая подсказка:
  «отойди назад», «не видно стоп», «мало света»).
- **SummaryScreen** — итог, разбивка по сегментам техник, средний каденс, длительность.
- **HistoryScreen** — список сессий из Room.
- **DevSettings** — переключатель детектора (MediaPipe/MoveNet), запись трейсов, показ FPS
  и времени инференса, экспорт трейса через `ACTION_SEND`.

Звук: короткий тон каждые 10 повторов через `ToneGenerator` (без TTS, без ассетов).

---

## 12. Производительность

- Разрешение анализа 640×480 или ниже.
- MediaPipe в режиме `LIVE_STREAM` с асинхронным колбэком — не блокировать camera thread.
- `STRATEGY_KEEP_ONLY_LATEST`: при перегрузе теряем кадры, а не копим задержку.
- Скакалка на 180 прыжков/мин — это 3 Гц. **При эффективном FPS ниже `descriptor.minFps`
  эмитить `QualityKind.LOW_FRAMERATE` и показывать предупреждение** — иначе счёт врёт молча.
- Адаптивная деградация при троттлинге: MediaPipe Lite → MoveNet Lightning int8 → снижение
  целевого FPS. Переключение в рантайме через интерфейс `PoseDetector`.
- Foreground service + wake lock для длинных сессий.

---

## 13. Этапы работы

Идти строго по порядку. **После каждого этапа: проект собирается, тесты зелёные, коммит.**

**M1 — Скелет проекта.**
Все Gradle-модули созданы и пусты, version catalog заполнен, `:core:model` и `:core:dsp` с типами,
`assemble` и `test` проходят. Detekt/ktlint настроены.
*Acceptance:* `./gradlew build` зелёный, модули `[pure Kotlin]` не имеют Android-зависимостей
(добавь тест или Gradle-проверку, которая это верифицирует).

**M2 — DSP-ядро и синтетические тесты.**
Ресемплер, фильтры, детектор пиков, оценка каденса, кросс-корреляция + генератор синтетических
сигналов + все тесты уровня 1.
*Acceptance:* синус известной частоты считается точно, тесты уровня 1 зелёные.

**M3 — Слой сигналов и анализаторы.**
`PoseNormalizer`, `SignalExtractor`, `LandmarkSchema`, `JumpRopeAnalyzer`, `TechniqueClassifier`,
`ThresholdAnalyzer` + конфиги squat/pushup. Генератор синтетических **трейсов** и golden-файлов.
Тесты уровня 2 + `:tools:replay` с CSV-метриками.
*Acceptance:* `./gradlew :tools:replay:run` печатает метрики по корпусу, MAE ≤ 2, F1 ≥ 0.95.
Всё это — **без единой строчки Android-кода**.

**M4 — Захват и детектор.**
CameraX, `CameraFrameSource`, `:pose:mediapipe`, склейка пайплайна, `TraceRecorder`.
*Acceptance:* приложение запускается, пишет трейс, трейс скармливается `:tools:replay`.

**M5 — UI.**
Все экраны, оверлей, Room, индикаторы качества.
*Acceptance:* полный сценарий «выбрал → отсчитал → увидел сводку → нашёл в истории».

**M6 — Расширяемость и второй детектор.**
`:pose:movenet`, переключение в рантайме, `VideoFileFrameSource`, инструментальные тесты.
*Acceptance:* **напиши в `docs/ADDING_EXERCISE.md` инструкцию и докажи её, добавив
четвёртое упражнение (jumping jacks) — diff должен затрагивать только новый модуль/конфиг.**

---

## 14. Правила работы

- **Не пиши код раньше, чем создан модуль и его тест-каркас.** Для `:core:dsp` и `:analysis:*` —
  сначала тест на синтетике, потом реализация.
- Коммиты — атомарные, по подзадачам, с осмысленными сообщениями. Один коммит = собирающееся
  состояние.
- Магические числа (пороги, окна, отсечки фильтров) — **только** в именованных константах
  в одном месте на модуль, с комментарием «почему такое значение». Их будут крутить.
- Каждый публичный интерфейс — KDoc с указанием, кто его вызывает и в каком потоке.
- Если библиотека тянет за собой сеть или Play Services — не подключай, напиши почему
  в `docs/DECISIONS.md`.
- В конце каждого этапа обновляй `README.md`: как собрать, как скачать модели, как запустить
  тесты, как записать и прогнать трейс.

## 15. Чего не делать

- Не добавляй разрешение `INTERNET`, аккаунты, синхронизацию, облако.
- Не пиши собственную реализацию pose estimation и не пытайся обучать модели.
- Не встраивай логику подсчёта в ViewModel или в CameraX-аналайзер — она живёт в `:analysis:*`.
- Не используй `System.currentTimeMillis()` внутри пайплайна.
- Не делай счёт по «изменению яркости» / frame-diff — это отдельная ветка, не в этом задании.
- Не добавляй double-unders, распознавание верёвки, мультиперсон, ачивки, шаринг.
- Не усложняй UI: он тут вторичен.

## 16. Итоговые артефакты

1. Собирающийся Android-проект в описанной модульной структуре.
2. `scripts/fetch_models.sh`.
3. `testdata/traces/` с синтетическими трейсами и golden-файлами.
4. `:tools:replay` с CSV-метриками.
5. CI-конфиг (GitHub Actions): `build` + `test` + detekt, **без эмулятора и без моделей**.
6. `README.md`, `docs/ARCHITECTURE.md`, `docs/ADDING_EXERCISE.md`, `docs/DECISIONS.md`.

---

**Начни с M1. Перед началом кратко перечисли, какие решения ты принял по неоднозначным местам,
и какие версии библиотек зафиксировал.**
