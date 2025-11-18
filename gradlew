#!/bin/sh
# Minimal gradlew wrapper script - will attempt to use existing gradle wrapper jar or fallback to system gradle
if [ -f "./gradle/wrapper/gradle-wrapper.jar" ]; then
  exec java -jar "./gradle/wrapper/gradle-wrapper.jar" "$@"
else
  # fallback to system gradle
  gradle "$@"
fi
