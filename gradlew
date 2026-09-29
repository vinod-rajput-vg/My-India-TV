#!/bin/sh
set -e
GRADLE_VERSION=9.6
GRADLE_HOME="$HOME/.gradle/wrapper/dists/gradle-$GRADLE_VERSION"
GRADLE_DIR="$GRADLE_HOME/gradle-$GRADLE_VERSION"
if [ ! -x "$GRADLE_DIR/bin/gradle" ]; then
  mkdir -p "$GRADLE_HOME"
  ZIP="$GRADLE_HOME/gradle.zip"
  if [ ! -f "$ZIP" ]; then
    curl -fsSL -o "$ZIP" "https://services.gradle.org/distributions/gradle-$GRADLE_VERSION-bin.zip"
  fi
  rm -rf "$GRADLE_DIR"
  unzip -q "$ZIP" -d "$GRADLE_HOME"
fi
exec "$GRADLE_DIR/bin/gradle" "$@"
