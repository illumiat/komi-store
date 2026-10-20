#!/bin/bash
for N in 954 955 966 934 964 959 968; do
  D="D:/komi-fix-$N"
  echo "############ #$N ############"
  cd "$D" || { echo "  ❌ 目录不存在"; continue; }
  OUT=$(./gradlew :composeApp:assembleDebug :core:presentation:compileKotlinJvm --console=plain 2>&1)
  if echo "$OUT" | grep -qE "BUILD SUCCESSFUL"; then
    echo "  ✅ BUILD SUCCESSFUL  @ $(git rev-parse --short HEAD)"
  else
    echo "  ❌ BUILD FAILED  @ $(git rev-parse --short HEAD)"
    echo "$OUT" | grep -E "^e: |error:|Unresolved|FAILED" | head -10 | sed 's/^/     /'
  fi
done
echo "############ 完成 ############"
