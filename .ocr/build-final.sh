#!/bin/bash
run() {
  local N="$1"; shift
  local TASKS="$1"
  cd "D:/komi-fix-$N" || { echo "  ❌ 目录不存在"; return; }
  echo "############ #$N  ($(git status --porcelain | grep -vc '^??') 文件未提交) ############"
  OUT=$(./gradlew $TASKS --console=plain 2>&1)
  if echo "$OUT" | grep -qE "BUILD SUCCESSFUL"; then
    echo "  ✅ BUILD SUCCESSFUL"
  else
    echo "  ❌ BUILD FAILED"
    echo "$OUT" | grep -E "^e: |error:|Unresolved|FAILED|tests? failed" | head -12 | sed 's/^/     /'
  fi
  # 测试结果摘要
  echo "$OUT" | grep -iE "tests? (completed|passed|failed)|[0-9]+ tests" | tail -3 | sed 's/^/     /'
}

run 954 ":composeApp:assembleDebug :core:presentation:compileKotlinJvm"
run 955 ":composeApp:assembleDebug :core:presentation:compileKotlinJvm"
run 966 ":composeApp:assembleDebug :core:presentation:compileKotlinJvm"
run 934 ":composeApp:assembleDebug :core:presentation:compileKotlinJvm :core:domain:jvmTest"
run 964 ":composeApp:assembleDebug :core:presentation:compileKotlinJvm"
run 959 ":composeApp:assembleDebug :core:presentation:compileKotlinJvm :core:presentation:jvmTest"
run 968 ":composeApp:assembleDebug :core:presentation:compileKotlinJvm"
echo "############ 全部完成 ############"
