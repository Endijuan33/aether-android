#!/usr/bin/env bash
set -euo pipefail

APK_PATH="$(ls dist-apk/*.apk 2>/dev/null | head -n 1 || true)"
if [ -n "$APK_PATH" ]; then
  APK_NAME="$(basename "$APK_PATH")"
  APK_SIZE="$(stat -c%s "$APK_PATH") bytes"
else
  APK_NAME="n/a (build did not produce an APK)"
  APK_SIZE="n/a"
fi
APK_SHA="$(cut -d' ' -f1 dist-sums/checksums.txt 2>/dev/null | head -n 1 || true)"
[ -n "$APK_SHA" ] || APK_SHA="n/a"
if [ "$RELEASE_BUILD_RESULT" != "success" ]; then
  SIGNING="unverified — release build did not succeed"
elif [ "$RELEASE_SIGNED" = "true" ]; then
  SIGNING="signed with apksigner (keystore from AETHER_KEYSTORE_BASE64)"
else
  SIGNING="unsigned — signing secrets not configured"
fi
{
  echo "## Release summary"
  echo "- Ref: \`$RELEASE_REF_NAME\` (event: $RELEASE_EVENT_NAME)"
  echo "- Verify (unit tests + lint): \`$RELEASE_VERIFY_RESULT\`"
  echo "- Build (assembleRelease): \`$RELEASE_BUILD_RESULT\`"
  echo "- Signing: $SIGNING"
  echo "- APK: \`$APK_NAME\`"
  echo "- Size: $APK_SIZE"
  echo "- SHA-256: \`$APK_SHA\`"
} >> "$GITHUB_STEP_SUMMARY"
