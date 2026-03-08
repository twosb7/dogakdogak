#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
LOG_DIR="$ROOT_DIR/build/hangul-loop-logs"
WORK_ROOT="$ROOT_DIR/build/hangul-loop-workdirs"
mkdir -p "$LOG_DIR"
mkdir -p "$WORK_ROOT"

buckets=(0 1 2 3)
pids=()
status=0

for bucket in "${buckets[@]}"; do
  log_file="$LOG_DIR/bucket-${bucket}.log"
  worker_dir="$WORK_ROOT/bucket-${bucket}"
  repo_dir="$worker_dir/repo"
  rm -rf "$worker_dir"
  mkdir -p "$repo_dir"
  echo "Preparing bucket ${bucket} workspace -> ${repo_dir}"
  rsync -a \
    --exclude '.git' \
    --exclude '.gradle' \
    --exclude 'build' \
    --exclude 'app/build' \
    --exclude 'node_modules' \
    "$ROOT_DIR/" "$repo_dir/"
  echo "Starting bucket ${bucket} -> ${log_file}"
  (
    cd "$repo_dir"
    HANGUL_LOOP=true ./gradlew :app:testDebugUnitTest \
      --tests "helium314.keyboard.latin.InputLogicTest.hangulEditingLoopBucket${bucket}"
  ) >"$log_file" 2>&1 &
  pids+=($!)
done

for i in "${!buckets[@]}"; do
  bucket="${buckets[$i]}"
  pid="${pids[$i]}"
  if wait "$pid"; then
    echo "Bucket ${bucket}: PASS"
    rm -rf "$WORK_ROOT/bucket-${bucket}"
  else
    echo "Bucket ${bucket}: FAIL (see $LOG_DIR/bucket-${bucket}.log, workspace $WORK_ROOT/bucket-${bucket}/repo)"
    status=1
  fi
done

exit "$status"
