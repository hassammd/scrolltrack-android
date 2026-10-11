#!/bin/bash
# Reel Tracker v1.0 — manual APK build (no Gradle)
set -e
export JAVA_HOME="$HOME/workspace/jdk/jdk-17.0.20.1+1"
export PATH="$JAVA_HOME/bin:$PATH"
SDK="$HOME/workspace/android-sdk"
ANDROID_JAR="$SDK/platforms/android-34/android.jar"
BT="$SDK/build-tools/34.0.0"
PROJ="$HOME/workspace/apk-build/reel-tracker"
cd "$PROJ"
rm -rf build && mkdir -p build/classes build/dex build/gen

echo "== keystore =="
if [ ! -f "$PROJ/debug.keystore" ]; then
  keytool -genkeypair -keystore "$PROJ/debug.keystore" -alias androiddebugkey \
    -storepass android -keypass android -keyalg RSA -keysize 2048 -validity 10000 \
    -dname "CN=ReelTracker Debug" >/dev/null 2>&1
fi
KS="$PROJ/debug.keystore"

echo "== aapt2 compile =="
"$BT/aapt2" compile --dir res -o build/res.zip

echo "== aapt2 link =="
"$BT/aapt2" link -o build/base.apk -I "$ANDROID_JAR" \
  --manifest AndroidManifest.xml --java build/gen build/res.zip

echo "== javac =="
javac -encoding UTF-8 -source 8 -target 8 -nowarn \
  -cp "$ANDROID_JAR" -d build/classes \
  build/gen/com/hatch/reeltracker/R.java \
  java/com/hatch/reeltracker/*.java

echo "== d8 =="
"$BT/d8" --min-api 26 --output build/dex $(find build/classes -name "*.class")

echo "== add dex =="
(cd build/dex && zip -q ../base.apk classes.dex)

echo "== zipalign =="
"$BT/zipalign" -f 4 build/base.apk build/aligned.apk

echo "== apksigner =="
"$BT/apksigner" sign --ks "$KS" \
  --ks-pass pass:android --key-pass pass:android \
  --out build/scrolltrack-v2.7.apk build/aligned.apk

echo "== verify =="
"$BT/apksigner" verify --print-certs build/scrolltrack-v2.7.apk | head -3
ls -la build/scrolltrack-v2.7.apk
