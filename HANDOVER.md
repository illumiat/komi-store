# komi-store 维护者交接文档

> 交接人：illumiat（QA / triage / release）
> 日期：2026-09-08
> 面向：接手本仓库维护工作的协作者（或未来回到此项目的自己）

---

## 一、项目结构

### 仓库与远端

| 名称 | 地址 | 用途 |
|---|---|---|
| `origin` | `github.com/kurikomi-labs/komi-store` | 上游主仓库（组织 2026-09-04 由 komi-store 改名，旧 URL 会重定向） |
| `fork` | `github.com/illumiat/komi-store` | 个人 fork，**所有 PR 头分支推这里** |

组织名变更注意：gh 回显的 URL 是 `github.com/komi-store/...`，API 调用仍用 `kurikomi-labs` 路径即可正常。

### 技术栈

- **Kotlin Multiplatform + Jetpack Compose Multiplatform**（Android / Desktop 双端）
- **Compose Navigation**（`NavHost` + `GithubStoreGraph`）
- **Koin**（DI）、**Room**（本地库，当前 schema v18）、**DataStore**（偏好）、**Coroutines Flow**（数据流）
- **Gradle 8.14 + version catalog**（`gradle/libs.versions.toml`）+ `build-logic` convention 插件
- Kotlin 目标：JVM（desktop）+ Android

### 模块布局

```
composeApp/                 # 应用壳：Main.kt（根 Composable）、MainViewModel、MainActivity
core/
  domain/                   # 纯领域逻辑：VersionMath、UpdateVerdict、InstalledAppUpdates、use_cases
  data/                     # 数据层：repository 实现（InstalledAppsRepositoryImpl 等）、Room DAO、Worker
  presentation/             # 共享 UI 基础
feature/                    # 每个功能一个三段式模块（domain / data / presentation）
  apps/  auth/  details/  dev-profile/  favourites/  feed/  home/
  profile/  recently-viewed/  repo-pages/  search/  starred/  tweaks/
```

关键约定：**feature 内部分层固定**，新增功能照抄现有结构；领域纯逻辑放 `core/domain`（可单测，如 `UpdateVerdict` 是纯 Kotlin object）。

### 代码风格红线（违反会被拒或被要求整改）

1. **无 KDoc、无函数/类文档**（CLAUDE.md:120）。inline 注释仅限：非显然不变量、棘手并发、workaround。判据："读代码能回答的 why，删掉注释"。
2. **布尔命名 `isXxx/hasXxx/canXxx`**（CONTRIBUTING.md:187）；私有状态下划线前缀 `_state`。
3. **提交信息**：`fix:`/`feat:` 前缀 + 小写祈使句，<72 字符，一句话说清 what。
4. **ktlint 全仓库强制**（根级 `./gradlew ktlintCheck`；注意模块级没有 ktlintCheck 任务）。
5. **注释政策的精神**：绝对化措辞（"only"、"never"）在后续演进中容易变成谎言——写注释时留诚实余地，改代码时回头核注释。

### 本地验证清单（PR 前必跑，本仓库无 CI 跑 PR）

```bash
./gradlew ktlintCheck                       # 风格
./gradlew :core:domain:jvmTest              # 领域测试（当前 79 个）
./gradlew :composeApp:compileKotlinJvm :composeApp:compileDebugKotlinAndroid   # 双端编译
# 改了 feature 模块再加对应 :feature:<x>:presentation:compileKotlinJvm
```

- JVM 测试任务名是 `:core:domain:jvmTest`（不是 allTests/jvm）。
- Android 编译任务名是 `compileDebugKotlinAndroid`（不是 compileKotlinDesktop）。
- 构建需要 `local.properties` 指向 Android SDK（git worktree 里不会自动有，要手动拷）。

---

## 二、PR 流程与控制方式

### 标准流程（CONTRIBUTING.md 摘要）

1. 从最新 `origin/main` 切分支：`git fetch origin && git checkout -b feat/123-xxx origin/main`
2. 保持 PR 单一逻辑变更；长出了就拆栈。
3. 本地检查全绿（见上清单）。
4. PR 描述五要素：**What / Why / How to test / 截图（UI 改动强制）/ `Closes #123`**。
5. **无 CI**——PR 构建质量全靠自查。
6. **CodeRabbit 自动评审**：每条评论要处理（修掉或有理有据地驳回），"LGTM" 式敷衍会被维护者打回。CodeRabbit 不总是对的，可以带着论据顶回去。
7. 人工评审意见用 follow-up commit 回应（评审期不 force-push；合并时自动 squash）。
8. **合并由维护者执行，Squash and merge**，分支自动删除。

### 会被拒的 PR（CONTRIBUTING.md:256）

- 混合关注点（refactor + feature + 依赖升级一锅炖）
- 新依赖没有在描述里给理由
- UI 改动没有截图
- 无视既有模式的代码（"为什么不是 XViewModel"、"为什么绕过 convention 插件"）
- 空/含糊提交信息、历史里留 WIP

### 本仓库 fork 工作流的实操要点（踩过的坑）

**推送目标**：PR 头分支在 fork 上，`git push origin <branch>` 会 403（对上游无权限）。一律 `git push fork <branch>`。

**并行 PR 的分支管理**：
- 多个 PR 在飞时**禁止直接切分支改代码**（工作区会串），用 `git worktree add .worktree-<id> fork/<branch>` 隔离编辑，用完 `git worktree remove --force` 清理；worktree 必须手动拷贝 `local.properties`。
- 多 PR 合并验证用本地合并分支（如 `test/combined-934-954-955`）：`git checkout -b test/combined-xxx origin/main` 后依次 `git merge --no-ff fork/<branch>`。三个在飞 PR（#934/#954/#955）改动文件集互不重叠，历来零冲突。
- 合并分支是**本地验证用**，默认不推 fork。

**GitHub API**：
- 桌面 GitHub connector 只有只读权限，写操作（评论、编辑正文、合并）一律 403。
- 用 gh CLI（已登录 illumiat，含 repo scope）。注意在 Bash 里相对路径 `--body-file` 会找不到文件，用 PowerShell 调完整路径 + 绝对路径：
  ```powershell
  & "C:\Program Files\GitHub CLI\gh.exe" pr edit 934 --repo kurikomi-labs/komi-store --body-file "D:\komi-store\xxx.md"
  ```
- PR 正文导出：`gh api repos/kurikomi-labs/komi-store/pulls/<n> --jq .body > file.md`，改完 `pr edit` 应用，再用 `--jq` 核实关键字段（updated_at、旧措辞残留 false）。

**行为铁律**（用户明令，违反已被纠正两次）：
- 未经用户确认，禁止向 GitHub 发任何写操作（评论、正文编辑、创建 PR、合并）。流程：起草 → 给用户过目 → 批准 → 执行。
- push 前也须确认。
- 自动审查报告只落本地文件，绝不直接发到 GitHub。

### 评审方法论（本项目沉淀，可直接复用）

**双轴审查**（engineering-code-review 技能）：Standards 轴（对照 CLAUDE.md/CONTRIBUTING.md + Fowler 坏味道基线）与 Spec 轴（对照 PR 正文逐条）并行跑两个子代理，报告不合并不排序。

**盲审操作要点**：
- 子代理只喂 diff 命令 + PR 正文导出文件，不给前轮结论、不给 severity 引导词（"吹毛求疵级""诋毁级"都不要）。
- **严重度引导语是噪声源**：实测"诋毁级"轮 ~60% 指控需实弹驳倒；校准轮噪声近零。默认校准口径。
- 收到报告先**实弹核验**再采纳：正则行为用 python 单测复现、协程时序读源码推演、对照 origin/main 分辨"新引入"还是"既有"。盲审也会虚报（如把 `CompletableDeferred.complete()` 后的 `await()` 判为"滞留"）。
- 区分三类发现：代码违约（改码）、正文与代码不符（改正文或改码取其一）、正文自相矛盾（通常改正文收窄承诺或改码补齐能力）。
- **PR 正文 Validation 段必须逐项对应当前 HEAD**：改了实现就要重核每条声明；没做过的验证不许写"已验证"，老验证数据不能替新代码背书。
- 停手判据：核心逻辑连续两轮翻不出新问题、剩下的都是接缝/措辞级 → 内部审查收敛，把增量留给上游人工评审。

**提交署名**：local git config 曾带入他人身份，2026-09-04 已全仓库重写为 illumiat。**每次 commit 前核对 `git config user.email`**（现应输出 `128163683+illumiat@users.noreply.github.com`）。

---

## 三、发布流程（release，QA/归你管）

CONTRIBUTING.md "Release flow"：维护者从 `main` 切，短稳定窗口后：

1. `composeApp/build.gradle.kts` 里 bump `versionName`/`versionCode`（当前 1.9.3 / 22）。
2. 打 tag `vX.Y.Z`。
3. Android 用签名 key 出 release 包；Desktop 出全套安装包（.exe/.msi/.dmg/.pkg/.deb/.rpm/.appimage/.tar.zst）。
4. Release notes 手写（用户视角，不是 commit 罗列）。
5. F-Droid 与应用内更新器自动跟进。

贡献者通常不碰这段，但要知道**功能进 main ≠ 立即发布**。

---

## 四、在飞 PR 状态快照（2026-09-08）

| PR | 主题 | head | 状态 |
|---|---|---|---|
| #934 | nightly/不透明 tag 的 publishedAt 检测 + 写入面 zone 化 | `0dad305` | 等上游复审 |
| #954 | 发布/公告日期按设备时区显示 | `5e993e9` | 等上游复审 |
| #955 | 启动 appearance gate（防首帧错主题 + 深链接冷启动崩溃） | `62a1f3a` | 等上游复审 |

三者互不冲突（文件集无交集），任意顺序合并无需 rebase 处理。#934 经历 3 轮双轴审查、#955 经历 2 轮，均已修复-推送-正文对齐-真机复检闭环。

### 历史遗留备份

- `backup/pre-author-rewrite-*` ×5：署名重写前的链，确认无误后可删。
- `backup/nightly-full-20260829`：早期完整方案。
- `fix/pending-install-claim-guard`：已推 fork、无 PR，状态未跟进。

---

## 五、设备实测速查

- 测试机：小米 23116PN5BC（shennong），ADB 序列 35299063，已装 debug 签名版（`adb install -r` 可直接覆盖升级，不用卸载）。
- 深链接格式：`githubstore://repo/<owner>%2F<repo>`、`githubstore://apps`、auth callback 等（manifest 注册 host：callback / repo / apps）。
- 冷启动深链接测试法：`adb shell am force-stop zed.rainxch.githubstore` → `adb shell am start -a android.intent.action.VIEW -d "<uri>"` → `uiautomator dump` 多时点采样 + logcat 查 FATAL。
- 冒烟：`adb logcat -c` → monkey 启动 → `pidof` + logcat。
- debug 包产物：`composeApp/build/outputs/apk/debug/composeApp-debug.apk`（~70MB）。
