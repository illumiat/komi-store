#!/bin/bash
# 主线程串行构建验证（Gradle 锁不能并行）
for N in 954 955 966 934 964 959; do
  D="D:/komi-fix-$N"
  echo "############ #$N ############"
  cd "$D" || { echo "  ❌ 目录不存在"; continue; }
  echo "  基线: $(git rev-parse --short HEAD)  改动: $(git status --porcelain | grep -vc '^??') 文件"
  OUT=$(./gradlew :composeApp:assembleDebug :core:presentation:compileKotlinJvm --console=plain 2>&1)
  if echo "$OUT" | grep -qE "BUILD SUCCESSFUL"; then
    echo "  ✅ BUILD SUCCESSFUL"
  else
    echo "  ❌ BUILD FAILED"
    echo "$OUT" | grep -E "^e: |error:|Unresolved|FAILED" | head -12 | sed 's/^/     /'
  fi
done
echo "############ 全部完成 ############"
