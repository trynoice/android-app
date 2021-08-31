#!/usr/bin/env bash

GRADLE_TASK=$1
TEST_RUNNER_ERROR_LOGS=./test-runner-error-logs.txt

if [ -z "$GRADLE_TASK" ]; then
  echo "Usage: $0 [UI TEST GRADLE TASK]"
  exit 1
fi

echo "waiting for a device..."
adb wait-for-device

echo "waiting for the device to boot..."
adb shell 'while [[ -z $(getprop sys.boot_completed) ]]; do sleep 1s; done'

echo "disabling animations..."
adb shell settings put global window_animation_scale 0.0
adb shell settings put global transition_animation_scale 0.0;
adb shell settings put global animator_duration_scale 0.0;

echo "disabling screen timeout..."
adb shell svc power stayon true;
adb shell input keyevent KEYCODE_WAKEUP;

echo "disabling immersive mode confirmations..."
adb shell settings put secure immersive_mode_confirmations confirmed

echo "begin collecting test runner logs..."
adb logcat -c # truncate old logs
adb logcat -v raw -v color -s "TestRunner:E" > "$TEST_RUNNER_ERROR_LOGS" &
LOGCAT_PID=$!

echo "starting $GRADLE_TASK gradle task..."
./gradlew "$GRADLE_TASK" --no-daemon --stacktrace
GRADLE_EXITCODE=$?

echo "stop collecting test runner logs..."
kill "$LOGCAT_PID"

if [ $GRADLE_EXITCODE -ne 0 ];  then
  echo ""
  echo "$GRADLE_TASK has failing tests!" >&2
  cat "$TEST_RUNNER_ERROR_LOGS" >&2
  exit 1
fi
