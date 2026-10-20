#!/bin/bash
for N in 964 968; do
  D="D:/komi-fix-$N"
  echo "############ #$N ############"
  cd "$D" || { echo "  ❌ 目录不存在"; continue; }
  echo "  基线: $(git rev-parse --short HEAD)  改动: $(git status --porcelain | grep -vc '^??') 文件"
  OUT=$(./gradlew :composeApp:assembleDebug :core:presentation:compileKotlinJvm --console=plain 2>&1)
  if echo "$OUT" | grep -qE "BUILD SUCCESSFUL"; then
    echo "  ✅ BUILD SUCCESSFUL"
  else
    echo "  ❌ BUILD FAILED"
    echo "$OUT" | grep -E "^e: |error:|Unresolved|FAILED" | head -14 | sed 's/^/     /'
  fi
done
echo "############ 完成 ############"
