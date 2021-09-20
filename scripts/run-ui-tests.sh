#!/usr/bin/env bash

# In summary, this script does the following.
# 1. waits for a device to be available and booted.
# 2. disables its animations, screen timeout and immersive mode confirmation.
# 3. collect device error (E) logs with 'TestRunner' tag in the background.
# 4. Run the specified Gradle test task
# 5. Print collected logs if the Gradle test task fails.

trap "exit" INT

GRADLE_TASK=$1
TEST_RUNNER_ERROR_LOGS=./test-runner-error-logs.txt

if [ -z "$GRADLE_TASK" ]; then
  echo "Usage: $0 [UI TEST GRADLE TASK]"
  exit 1
fi

echo "waiting for a device..."
adb wait-for-device

echo "waiting for the device to boot..."

# shellcheck disable=SC2016
adb shell 'while [[ -z $(getprop sys.boot_completed) ]]; do sleep 1s; done'

echo "disabling animations..."
adb shell settings put global window_animation_scale 0.0
adb shell settings put global transition_animation_scale 0.0;
adb shell settings put global animator_duration_scale 0.0;

echo "disabling screen timeout..."
adb shell svc power stayon true;
adb shell input keyevent KEYCODE_WAKEUP;

echo "disabling immersive mode confirmations and spell-checker..."
adb shell settings put secure immersive_mode_confirmations confirmed
adb shell settings put secure spell_checker_enabled 0

echo "begin collecting test runner logs..."
adb logcat -c # truncate old logs
adb logcat -v raw -v color -s "TestRunner:*" > "$TEST_RUNNER_ERROR_LOGS" &
LOGCAT_PID=$!

echo "starting $GRADLE_TASK gradle task..."
./gradlew "$GRADLE_TASK" --no-daemon --stacktrace
GRADLE_EXITCODE=$?

echo "stop collecting test runner logs..."
kill "$LOGCAT_PID"

if [ $GRADLE_EXITCODE -ne 0 ];  then
  echo ""
  echo "gradle task '$GRADLE_TASK' has failing tests!" >&2
  cat "$TEST_RUNNER_ERROR_LOGS" >&2
  exit 1
fi
