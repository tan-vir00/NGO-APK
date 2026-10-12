#!/bin/sh
# Convenience launcher. The standard Gradle wrapper JAR is not bundled in this environment.
# Open this project in Android Studio, or install Gradle 8.9 and run `gradle <task>`.
if command -v gradle >/dev/null 2>&1; then
  exec gradle "$@"
fi
echo "Gradle is not installed and the standard wrapper JAR is not included. Open this project in Android Studio to sync/build, or install Gradle 8.9." >&2
exit 1
