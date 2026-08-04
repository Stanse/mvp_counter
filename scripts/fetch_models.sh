#!/usr/bin/env bash
# Downloads the on-device pose models into app/src/main/assets/models/.
#
# Models are never committed (see .gitignore) and the app never has INTERNET permission, so this
# script is the only way they reach a dev machine or CI cache before building. If a model is
# missing at runtime, the app shows an error screen linking back here instead of crashing.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
MODELS_DIR="$SCRIPT_DIR/../app/src/main/assets/models"
mkdir -p "$MODELS_DIR"

POSE_LANDMARKER_URL="https://storage.googleapis.com/mediapipe-models/pose_landmarker/pose_landmarker_lite/float16/latest/pose_landmarker_lite.task"
POSE_LANDMARKER_DEST="$MODELS_DIR/pose_landmarker_lite.task"

MOVENET_URL="https://www.kaggle.com/api/v1/models/google/movenet/tfLite/singlepose-lightning-tflite-int8/4/download"
MOVENET_DEST="$MODELS_DIR/movenet_singlepose_lightning_int8.tflite"

echo "==> Fetching MediaPipe pose_landmarker_lite.task"
curl -fL --progress-bar -o "$POSE_LANDMARKER_DEST" "$POSE_LANDMARKER_URL"
echo "    saved to $POSE_LANDMARKER_DEST"

echo "==> Fetching MoveNet singlepose lightning int8 .tflite"
if [[ -n "${KAGGLE_USERNAME:-}" && -n "${KAGGLE_KEY:-}" ]]; then
  if curl -fL --progress-bar -u "$KAGGLE_USERNAME:$KAGGLE_KEY" -o "$MOVENET_DEST" "$MOVENET_URL"; then
    echo "    saved to $MOVENET_DEST"
  else
    echo "    ERROR: Kaggle download failed even with credentials set. The model may have moved -" >&2
    echo "    check https://www.kaggle.com/models/google/movenet manually." >&2
    exit 1
  fi
else
  cat >&2 <<'EOF'
    SKIPPED: MoveNet now lives behind Kaggle's model hosting, which requires an API token even
    for public models (the old tfhub.dev direct-download URLs are gone).

    To fetch it:
      1. Create a Kaggle account and an API token: https://www.kaggle.com/settings -> "Create New Token"
      2. Export the credentials from the downloaded kaggle.json:
           export KAGGLE_USERNAME=<username>
           export KAGGLE_KEY=<key>
      3. Re-run this script.

    The app runs fine on MediaPipe alone (the default detector); MoveNet is the optional
    fallback (see PoseDetector switching in DevSettings, spec §12) and its absence is reported
    to the user as a normal error screen, not a crash.
EOF
fi

echo "==> Done. Model files are gitignored - re-run this script after a clean checkout."
