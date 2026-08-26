#!/usr/bin/env bash
# release.sh — clean build, sign, align, verify a Player Zero for Karoo release APK.
# Always cleans first (Kotlin constant-folds BuildConfig.VERSION_NAME; incremental builds ship stale banners).
#
# Needs: ANDROID_HOME (default /opt/android-sdk), GITHUB_TOKEN with read:packages (or `gh auth token`),
#        PLAYERZERO_KEYSTORE_PASS or ~/.claude/.secrets/velovigil-keystore-pass (same NorthWoods keystore).
# Usage: scripts/release.sh            OUT_DIR=/dir scripts/release.sh  (also copies the APK there)
set -euo pipefail
cd "$(dirname "$0")/.."

export ANDROID_HOME="${ANDROID_HOME:-/opt/android-sdk}"
export GITHUB_USER="${GITHUB_USER:-NorthwoodsSentinel}"
export GITHUB_TOKEN="${GITHUB_TOKEN:-$(gh auth token 2>/dev/null || true)}"
[ -n "$GITHUB_TOKEN" ] || { echo "no GITHUB_TOKEN (read:packages) — karoo-ext dependency will 401" >&2; exit 2; }

KS="velovigil-release-v2.keystore"
ALIAS="velovigil"
PASS="${PLAYERZERO_KEYSTORE_PASS:-$(cat "$HOME/.claude/.secrets/velovigil-keystore-pass" 2>/dev/null || true)}"
[ -n "$PASS" ] || { echo "no keystore password" >&2; exit 2; }

VERSION=$(grep -E 'versionName = "' app/build.gradle.kts | sed -E 's/.*"([^"]+)".*/\1/')
BT=$(ls -d "$ANDROID_HOME"/build-tools/* | sort -V | tail -1)
O=app/build/outputs/apk/release
NAME="player-zero-karoo-v$VERSION"

echo "== clean + assembleRelease ($VERSION)"
./gradlew clean assembleRelease --no-daemon -q
echo "== zipalign"
"$BT/zipalign" -f -p 4 "$O/app-release-unsigned.apk" "$O/$NAME-aligned.apk"
echo "== sign ($KS / $ALIAS)"
"$BT/apksigner" sign --ks "$KS" --ks-key-alias "$ALIAS" --ks-pass "pass:$PASS" --key-pass "pass:$PASS" \
  --out "$O/$NAME.apk" "$O/$NAME-aligned.apk"
echo "== verify"
"$BT/apksigner" verify --print-certs "$O/$NAME.apk" | grep -E "SHA-256" || true
BAD=$(for d in $(unzip -l "$O/$NAME.apk" | grep -o 'classes[0-9]*\.dex'); do unzip -p "$O/$NAME.apk" "$d" | strings | grep -E "^v0\.[0-9]+\.[0-9]+"; done | grep -v "^v$VERSION" || true)
[ -z "$BAD" ] || { echo "STALE VERSION LITERAL IN APK: $BAD" >&2; exit 3; }
ls -la "$O/$NAME.apk"
if [ -n "${OUT_DIR:-}" ]; then cp "$O/$NAME.apk" "$OUT_DIR/" && echo "copied to $OUT_DIR"; fi
