# 版本比较：工况全集与重新设计

> 起因：MajdataPlay 用户装 nightly 版后想回稳定版，被弹「需卸载后重装」。
> 教训：此前是**先设计、后踩雷**——每次探针都翻出一个新意外。本文档改为**先穷举工况，再谈设计**。
> 全部结论均来自实证（真实仓库采样 + 现有实现的行为探针），非推断。

---

## 一、实证基础

### 语料来源

用 GitHub API 采样 9 个真实仓库的 release/tag：MajdataPlay、komi-store、kotlinx.coroutines、vscode、zed、termux-app、Neo-Store、Droid-ify、Fossify/Calendar。共 40+ 种不同形态。

### 现有实现的行为基线（40 个真实 tag 全量喂入）

| tag | normalize 结果 | detectScheme | isTimestampTracked | cmp(2.0.2, it) |
|---|---|---|---|---|
| `1.2.3` / `v1.2.3` | `1.2.3` | SemVer | false | 1 |
| `1.11.0-rc02` | `1.11.0-rc02` | SemVer | false | 1 |
| `1.9.0-RC.2` | `1.9.0-RC.2` | SemVer | false | 1 |
| `1.3.0-M2` | `1.3.0-M2` | SemVer | false | 1 |
| `v0.5.3-fix` | `0.5.3-fix` | SemVer | false | 1 |
| `v0.5.9.5`（4 段） | `0.5.9.5` | SemVer | false | 1 |
| `v0.117`（缺 patch） | `0.117` | SemVer | false | 1 |
| `v14`（仅 major） | `14` | SemVer | false | -1 |
| `Alpha4.7.4` | **`4.7.4`** | Unknown | false | -1 |
| `Alpha4.7.36` | **`4.7.36`** | Unknown | false | -1 |
| `Alpha4.7.18E` | **`4.7.18`** | Unknown | false | -1 |
| `nightly` | `nightly` | Unknown | **true** | -60 |
| `rolling` | `rolling` | Unknown | **true** | -64 |
| `dev` / `canary` | 同名字符串 | Unknown | **true** | 负 |
| **`latest`** | `latest` | Unknown | **false** | -58 |
| **`edge`** | `edge` | Unknown | **false** | -51 |
| `26.08.11f15e4` | 原样 | Unknown | true | -8 |
| `1.2.3fabc` | 原样 | Unknown | true | 1 |
| `2.0.9-1c19925b5` | 原样 | **SemVer** | false | -1 |
| `vsda-v1.39.1` | **`1.39.1`** | **Unknown** | false | 1 |
| `release/1.46.0` | `1.46.0` | SemVer | false | 1 |
| `translation/20172701.01` | `20172701.01` | Unknown | false | -1 |
| `master-with-conflict-190418` | **`190418`** | Unknown | false | -1 |
| `1.2.3+build5` | `1.2.3` | SemVer | false | 1 |
| `2026-08-11` | `2026.08.11` | CalVer | false | -1 |
| `20260811` | `20260811` | CalVer | false | -1 |

### 从基线里读出的硬事实

1. **`latest` 与 `edge` 不在滚动标记表内**——`isTimestampTracked=false`。但两者是最常见的滚动 tag 之一。夜更仓库若用 `latest` 而非 `nightly`，#934 的整条时间戳路径**完全不触发**。
2. **`normalizeVersion` 与 `detectScheme` 对同一输入不一致**：`vsda-v1.39.1` 被 normalize 成 `1.39.1`，detectScheme 却判 `Unknown`。两个函数走了不同的清洗管线。
3. **单词前缀是幻觉制造机**：`Alpha4.7.4 → 4.7.4`、`Alpha4.7.18E → 4.7.18`（`E` 被吃掉）。一个 2025 年的旧系列被并进数字序列，于是"比 2.0.2 大"。
4. **`master-with-conflict-190418 → 190418`**：把分支名里的日期当成了版本号。
5. **不可解析标签的 cmp 值是原始字符串差值**（-60、-58、-51…）——这是"没有排序"的直接证据。

---

## 二、不变量核查（形式化）

对 14 个代表 tag 做全量两两验证：

| 不变量 | 违反数 | 判定 |
|---|---|---|
| **反对称性** `cmp(a,b) == -cmp(b,a)` | **0** | ✅ 未违反 |
| **传递性** `a>b ∧ b>c ⇒ a>c` | **18** | ❌ **违反** |

传递性反例（真实）：`10.0.0 > 9.0.0 > 26.08.11f15e4`，但 `10.0.0 < 26.08.11f15e4`。

机理：比较器的选择依赖"这一对能不能都解析"——
- `10.0.0` vs `9.0.0`：都能解析 → 数值比较 → 10 > 9
- `9.0.0` vs `26.08.11f15e4`：后者不可解析 → 字符串比较 → `'9' > '2'`
- `10.0.0` vs `26.08.11f15e4`：字符串比较 → `'1' < '2'` → 反转

**更正**：我此前对你说这"违反反对称性"——**那是错的**。反对称性 0 违反；真正的违反是**传递性**（18 例）。结论方向不变（排序无意义），但表述必须准确。

**传递性是排序的最低门槛。它破了 = 系统根本不存在"谁更新"这个概念**，只有一个逐对变化、互相矛盾的判断。

---

## 三、工况全集（穷举）

### 维度 1：tag 形态（语法）

| # | 类别 | 真实样本 | 可定序？ |
|---|---|---|---|
| 1 | 纯 semver | `1.2.3` `v1.2.3` `1.137.0` | ✅ |
| 2 | semver 段数可变 | `v14`（1 段）`v0.117`（2 段）`v0.5.9.5`（4 段） | ✅ 需容错 |
| 3 | semver + prerelease 词 | `1.11.0-rc02` `1.9.0-RC.2` `1.3.0-M2` `1.2.0-alpha-2` `v1.20.1-pre` `v0.5.3-fix` | ✅ 需归一化词表 |
| 4 | semver + hash prerelease | `2.0.9-1c19925b5` | ⚠️ 需特殊规则（GH#729） |
| 5 | 数字 + 哈希尾 | `26.08.11f15e4` `1.2.3fabc` | ❌ 不可序（时间戳域） |
| 6 | CalVer（连字符/点/整数） | `2026-08-11` `26.08.11` `20260811` | ⚠️ 跨族比较语义待定 |
| 7 | 滚动标记 | `nightly` `rolling` `dev` `canary` **`latest`** **`edge`** | ❌ 不可序 |
| 8 | 单词前缀 | `Alpha4.7.4` `vsda-v1.39.1` `release/1.46.0` | ❌ 语义不可判定 |
| 9 | 单词后缀（变体） | `Alpha4.7.18E` | ❌ 语义不可判定 |
| 10 | 路径命名空间 | `release/1.46.0` `translation/20172701.01` | ❌ |
| 11 | 分支式 | `master-with-conflict-190418` | ❌ |
| 12 | build metadata | `1.2.3+build5` | ✅（应忽略 + 之后） |
| 13 | 纯 commit hash | （未见，理论存在） | ❌ |
| 14 | 完全任意字符串 | 任意 | ❌ |

### 维度 2：数据可用性

| # | 状态 | 真实案例 |
|---|---|---|
| A | 两侧 tag 都在 release 列表内 | 常态 |
| B | 已装 tag 不在列表（release 被删/重发/掉出窗口/缓存） | **MajdataPlay 的 nightly** ← 本次事故 |
| C | 远端 tag 不在列表（release 搜不到） | — |
| D | versionCode 已知（两侧） | 常态 |
| E | versionCode 未知（远端为 null） | MajdataPlay 的 `latestVersionCode=None` |
| F | publishedAt 缺失 | — |
| G | 列表为空 / 只有一条 | 新仓库 |
| H | 同一 build 打了两个不同 tag | **MajdataPlay：nightly 与 2.0.2 同在 commit 79abbd1** |

### 维度 3：消费场景（同一问题，不同策略）

| 场景 | 位置 | 未知时的正确策略 |
|---|---|---|
| 检测更新 | `UpdateVerdict.decide` | 沉默（不误报） |
| 安装守卫 | `VersionHelper.isDowngradeVersion` | 放行（交给平台拒绝） |
| 外部安装同步 | `ExternalInstallVerdict` | 不认更新 |
| 详情页分类 | `DetailsViewModel` | 按 prerelease 旗标分类，不排序 |
| 列表页排序 | `AppsRepositoryImpl` | 不依赖顺序 |

### 维度 4：方向与相等

| # | 要求 |
|---|---|
| R1 | 每个判决必须正反一致（反对称） |
| R2 | 可比较集合内必须传递 |
| R3 | `Same` 不能因前缀/大小写/变体后缀差异而误判为不同 |
| R4 | 同一 build 不同 tag（维度 2-H）应判 `Same`，不是"降级" |

---

## 四、核心判断：问题问错了

| | 系统在问 | 工况能回答 |
|---|---|---|
| 命题 | **A 比 B 新吗？**（顺序） | **A 和 B 是同一个吗？**（差异） |
| 前提 | 存在全序 | 只需等价关系 |
| 面对任意 tag | 必然猜 | 可靠 |

第三方 tag 是随手写的字符串：格式会变、会重发、会删、会改方案。**对这样一个输入空间，"谁更新"不是难，是不可能的。**

而现有系统把"不可能"包装成了"看起来能"——用字符串比较兜底，于是产生了传递性违反 18 例，以及 MajdataPlay 那次虚构的降级。

---

## 五、决定性证据：versionCode 才是真值

MajdataPlay 事故的完整还原：

```
2.0.2 的 release 资产名：MajdataPlay-Android-2.0.2-20260913-79abbd1-nightly.arm64-v8a.apk
                                                    └── 构建自 commit 79abbd1
79abbd1 的提交计数 = 3740   →  CI 公式 versionCode = 3740 + 100000 = 103740
设备已装 nightly            →  versionCode = 3733 + 100000 = 103733
                                              ↓
                                      103740 > 103733  → 系统会接受为升级
```

**卸载要求 100% 由假判定造成**；平台从来就没打算拒绝。而且开发者把最后的 nightly 与 2.0.2 打在同一 commit（同一次构建），"降级"在物理上就不存在。

**versionCode 单调、由平台保证、且是 Android 实际判定升降级的依据。** 它就是"最全面极端情况"下唯一稳定的锚点。

---

## 六、设计

### 分层降级（按可靠性排序，绝不越级）

```
① 同一性判定   两侧可规范化且相等 → Same        （最可靠，只需等价）
② versionCode  两侧已知 → 数值比较              （平台真值，MajdataPlay 的答案）
③ 时间戳       已装侧 stored publishedAt vs 候选 publishedAt   （需新增字段，见下）
④ tag 数值序   两侧同族可解析（semver/calver）  → Newer/Older
⑤ 未知         Indeterminate                   （不猜、不比较字符串）
```

**五层全落空时返回 `Indeterminate`，由调用方按场景决定**（沉默 / 放行 / 不认账）。

#### ③ 时间戳的实际语义与缺口（重要更正）

现有代码里的时间戳比较**不是**"候选 vs 已装"的排序，而是 **release 流的变化检测**：

```
matched.publishedAt    ← 现在看到的候选 release
   vs
stored.publishedAt     ← 上次看到的最新 release（字段 latestReleasePublishedAt）
```

`latestReleasePublishedAt` 全仓只有一个写入点（`InstalledAppsRepositoryImpl.kt:374`，位于 `updateVersionInfo`），即"上次扫描时看到的最新 release"。

**缺口：没有任何字段记录"当前安装的那个 release 是何时发布的"。**

| 现有时间字段 | 含义 | 可否用于排序 |
|---|---|---|
| `installedAt: Long` | 装到本设备的时间（本地时钟） | ❌ 今天装旧版本也会"很新" |
| `lastCheckedAt: Long` | 上次检查时间 | ❌ |
| `latestReleasePublishedAt: String?` | 最新 release 的发布时间 | ⚠️ 仅候选侧 |

后果：MajdataPlay 案例中，已装 tag 是 `nightly`，其 Release 已被删除（`releases/tags/nightly` = 404），**该侧没有任何 publishedAt 可查**——只有一侧有时间戳，时间戳路径自然无法作答。

**修法**：安装确认时把所装 release 的 `publishedAt` 一并存下（新增 `installedReleasePublishedAt`）。核对过 `InstalledAppsRepositoryImpl.kt:475` 的 `.confirmInstall(...)` 调用：它已持有 tag/versionName/versionCode，但**未传递 publishedAt**——尽管所在上下文正在处理一个带 publishedAt 的 release。

补齐后：已装 nightly 存下其发布时刻（如 `09-13T09:00Z`），候选 2.0.2 为 `09-13T13:12:13Z` → 可正确判定候选更新，**且不依赖那个已被删除的 Release**。

**局限（必须写明）**：`publishedAt` 是 release 发布时间，**不是构建时间**。把旧构建重新发成新 release 会让时间戳撒谎——即工况 2-H（同一 build 打两个 tag），MajdataPlay 实际如此（`nightly` 与 `2.0.2` 同在 commit `79abbd1`）。因此 ② 必须排在 ③ 之前，二者冲突时以 versionCode 为准。


### 必须满足的不变量（设计约束）

| 编号 | 不变量 | 用于验证 |
|---|---|---|
| **I1** | 反对称：`order(a,b)` 与 `order(b,a)` 互为反 | 回归测试 |
| **I2** | 传递：全序集合内可比关系传递 | **18 例反例必须归零** |
| **I3** | 名称无关：规范化只做无害变换，不做语义猜测 | 禁 `Alpha→4.7` 类前缀剥离 |
| **I4** | 不猜：任何路径都不得退化为字符串比较 | grep + 测试 |
| **I5** | 平台一致：versionCode 可用时，结论不得与 Android 相悖 | MajdataPlay 案例 |
| **I6** | 未知一等公民：`Indeterminate` 是返回值，不是异常/兜底 | 类型系统 |

### 工况 × 期望行为矩阵

| 工况 | 期望 | 现实现状 |
|---|---|---|
| semver vs semver | ← 数值序 | ✅ |
| semver 段数可变 | ← 容错比较 | ✅ |
| prerelease 词形态多样 | ← 归一化后比较 | ✅（词表待扩） |
| semver + hash prerelease | ← 特殊规则，不同侧不比较 | ✅（GH#729） |
| 滚动标记（含 `latest`/`edge`） | → Indeterminate | ⚠️ `latest`/`edge` 未识别为滚动 |
| 单词前缀/后缀（Alpha…） | → Indeterminate | ❌ 现为 `4.7.4` 幻觉序 |
| 路径命名空间 / 分支式 | → Indeterminate | ⚠️ 部分被截成数字 |
| 同 build 不同 tag | → **Same** | ❌ 现走序比较→Indeterminate |
| 已装 tag 不在列表 | → Indeterminate | ❌ 现字符串比较 |
| versionCode 已知 | → **优先用它** | ❌ 完全未使用 |
| 跨族（semver vs calver） | → 待定（见未决） | ❌ 字符串比较 |
| 两侧完全相同 | → Same | ✅ |

---

## 七、与现状的差距（按优先级）

| # | 差距 | 影响 | 证据 |
|---|---|---|---|
| 1 | **安装守卫的字符串比较回退** | 用户被虚构降级拦死 | 已修（b4878ee），但属打补丁 |
| 2 | **versionCode 未被用作判据** | 放弃唯一可靠锚点 | MajdataPlay：103740>103733 |
| 3 | **单词前缀被剥成数字** | 旧系列伪装成新版本（60 对行为变动） | `Alpha4.7.4 → 4.7.4` |
| 4 | **传递性违反 18 例** | 排序无意义 | 不变量探针 |
| 4b | **已装侧无 publishedAt 记录** | 时间戳路径对不可解析 tag 失效 | `InstalledApp` 无 `installedReleasePublishedAt`；`confirmInstall` 未传 publishedAt（`:475`） |
| 5 | `latest`/`edge` 未列为滚动标记 | #934 路径不触发 | 行为基线 |
| 6 | `normalize` 与 `detectScheme` 管线不一致 | 同一输入两个答案 | `vsda-v1.39.1` |
| 7 | 同 build 不同 tag 不判 Same | 无谓的更新提示/拦截 | MajdataPlay 79abbd1 |
| 8 | release 与 tag 流不一致 | 可能漏更新 | MajdataPlay：release 停 2.0.2，tag 有 Alpha4.7.36 |
| 9 | 跨族比较语义未定义 | 未定义行为 | CalVer vs SemVer |

---

## 七之二、保证性逻辑：用平台自己的判据（决定性发现）

前面所有方案的共同弱点：**都在猜"哪个更新"**。而有一个判据不需要猜，因为它就是平台自己用的那个。

### 结论

**安装守卫的判决不应比较 tag，而应比较"已下载 APK 的 versionCode"与"已安装包的 versionCode"——这正是 Android 自己判定升降级的方式。**

### 为什么这是"保证"

| 性质 | 说明 |
|---|---|
| 同源 | 与 Android 的接受/拒绝使用**同一个标识符**，不是代理指标 |
| 可判定 | `code_new >= code_old` ⇔ 平台会接受 ⇒ 永不误弹"需卸载"；`<` ⇒ 平台会拒绝 ⇒ 警告真实正确 |
| 与 tag 无关 | nightly、Alpha4.7.18E、任意字符串——**根本不读 tag**，故对任意第三方方案免疫 |
| 是事实 | 从产物本身读取，非启发式推断 |
| 跨端一致 | 签名不匹配（"需卸载"的另一半原因）同样可判：`ApkInspection.signingFingerprint` vs 已装指纹 |

**关键：我们不需要比平台更聪明，只需要不比平台更蠢。** 镜像平台的判据，就永远不会与平台的实际行为相悖（不变量 I5 由此可证）。

### 基础设施已全部就绪（实测）

| 组件 | 位置 | 状态 |
|---|---|---|
| `ApkInspector.inspectFile(filePath)` → `ApkInspection(versionCode, signingFingerprint, …)` | `core/domain/.../system/ApkInspector.kt` | ✅ 已实现 |
| `inspectInstalled(packageName)` | 同上 | ✅ 已实现 |
| `AndroidApkInspector`（`getPackageArchiveInfo` 读文件） | `core/data/src/androidMain/.../services/` | ✅ 已实现 |
| DI 注册 `single<ApkInspector>` | `PlatformModule.android.kt:140` / `.jvm.kt:74` | ✅ 已注册 |
| 现有消费方 | `ApkInspectSheet`（用户手动查看 APK 信息） | ✅ 已用于 UI |

**即：读 APK versionCode 的能力早就有了、已注册、已有 UI 入口——只是从没用在这个判决上。**

### 接入点（实测）

```kotlin
// DetailsViewModel.kt:1030
val filePath = downloader.getDownloadedFilePath(primary.name)
    ?: throw IllegalStateException("Downloaded file not found")

// ← 此处：APK 已落盘，filePath 可用，安装尚未发生
//   apkInspector.inspectFile(filePath)        → 候选 versionCode
//   apkInspector.inspectInstalled(pkgName)    → 已装 versionCode
//   比较 → 与平台判决一致

installer.openWithExternalInstaller(filePath)
```

### 由此得出的两阶段结构

| 阶段 | 时点 | 可用证据 | 允许的行为 |
|---|---|---|---|
| **预检** | 下载前（现 `install()` 的守卫位置） | 只有 tag | **不得阻断**（无 versionCode，猜必错）；至多提示 |
| **终检** | 下载后、安装前（`:1030`） | 真实 versionCode + 指纹 | **权威**：可正确警告/放行 |

预检的"不得阻断"正是 `b4878ee` 所做的事——它不是补丁，而是**该阶段唯一诚实的策略**。

### 对检测层的影响

检测层（`UpdateVerdict`）无法使用 versionCode：远端 release 的 versionCode 不给过下载就拿不到（现有 `resolvedLatestVersionCode` 只在 tag 未变时沿用旧值，tag 一变即为 `null`，`:360`）。因此检测层只能继续用 tag/时间戳，**并在未知时沉默**——这正是 #934 的现有行为，无需改动。

**分界清晰**：检测层回答"要不要告诉用户有新的"（猜错了无害：漏报可手动查），终检回答"这个能不能装"（猜错了有害：误弹卸载）。**只有后者需要保证，而它恰好可以保证。**

---

## 七之三、卸载不是解法：现状的破坏性设计（决定性）

前面承认了判定会错。但比"判定错"更严重的是**系统对判定的处置方式**。

### 现状（实测）

```kotlin
// DetailsViewModel.kt:1471
if (isDowngrade) {
    _state.update { it.copy(downgradeWarning = DowngradeWarning(...)) }
    return                       // ← 中止安装，非提示
}
```

弹窗仅两个出口（`DetailsRoot.kt:205-250`）：

| 按钮 | 实际动作 |
|---|---|
| 「卸载后安装」 | `installer.uninstall(packageName)`（`DetailsViewModel.kt:1435`）→ **完整卸载，应用数据一并丢失**（无 `-k`） |
| 「取消」 | 不安装 |

**没有"仍然安装"这一选项。**

### 代价对比

| 处置 | 代价 |
|---|---|
| 让安装失败（平台拒绝） | ≈ 0：一条错误信息，数据完好，可重试 |
| 强制先卸载 | **删除该应用全部数据，不可逆** |

为了安装一个**可能完全能装上的版本**，用户被要求先销毁自己的数据。

### 这独立于判定是否正确

即使判定**准确**（确实降级），强制卸载依然是错误设计：

1. 用户可能**故意降级**（回滚有问题的版本）——这是合法操作；
2. 平台自己会拒绝（`INSTALL_FAILED_VERSION_DOWNGRADE`），该错误信息明确且零破坏性；
3. 卸载会丢数据，而"装不上"只是失败——**后者永远优于前者**。

### 正确形态

```
「这个版本比你当前安装的旧」
   [仍然安装]   ← 主操作，默认可选
   [取消]
   卸载只是可选建议，且必须说明会丢数据
```

三条依据：判定可能错（MajdataPlay 已证）／用户可能有意降级／平台才是最终裁决者。

### 修正后的分层（最终）

| 层 | 作用 | 能否阻断 |
|---|---|---|
| 后台通知 | 有没有新的 | — |
| 列表 + 日期 | 用户自行判断（`VersionPicker.kt:254` 已展示日期） | — |
| 下载前提示 | "看起来更旧"（publishedAt） | ❌ 绝不 |
| 下载后提示 | versionCode 表明平台会拒 → "这个装不上" | ❌ 绝不 |
| 平台 | 最终裁决 | 由它决定 |

**versionCode 的定位由此降级为"提升提示质量"**——把"看起来更旧（可能错）"升级为"平台会拒绝（确定）"。它不承担阻断职责，因为**没有任何情形需要阻断**。

`b4878ee` 解除了错误拦截，但弹窗本身"只有卸载"的结构缺陷仍在——**任何一次真实降级，用户依然只有"删数据"或"取消"两条路**。这是比版本比较更该修的东西。

---

## 八、未决问题（需决策，不能由实现代劳）

1. **跨族比较**：`2026-08-11`（CalVer）与 `2.0.2`（SemVer）谁新？语义上不可判定 → 建议 `Indeterminate`？
2. **单词前缀数字是否可用**：`Alpha4.7.4` vs `Alpha4.7.36` 同样是前缀，但确实是同一系列的时间序 → 用词内数字比较，还是只用时间线证据？
3. ~~**versionCode 如何获取**~~ → **已解答，见第七之二节**：下载后安装前读取（`ApkInspector` 已具备），检测层确实无锚点且不需要——漏报无害，误弹才有害。
4. **release vs tag 流**：是否应同时消费 `/tags`？会带来大量误报。
5. **`latest`/`edge`/`rolling` 等滚动标记表**：是否可枚举完？还是应改为"语义上不含版本号的即视为滚动"？
6. **跨通道意图**：`nightly` → 稳定版 该报更新（用户想换通道）还是沉默（尊重通道选择）？#934 选了沉默并钉了测试，但安装守卫的拦截使这个选择变成了"什么都拿不到"。

---

## 九、我的错误更正

| 我说过的 | 事实 |
|---|---|
| "违反反对称性" | ❌ 反对称 0 违反；真违反是**传递性（18 例）** |
| "`versionsReconcilable` 可退化为 `order()==Indeterminate` 的别名" | ❌ 两个方向都分歧，必须组合式 |
| "934 内部需要重构" | ⚠️ 方向对（知识碎片化属实），但我当时并未穷举工况就下了结论 |
| "加一个 `VersionOrder` 模块就解决" | ❌ 它只统一了 API 形状，**没有回答"不可解析时怎么办"**；且擅自改了 60 对行为 |
| 把 #934 的时间戳说成"候选 vs 已装"的排序 | ❌ 实际是 release 流的变化检测（`matched.publishedAt` vs 上次的 `latestReleasePublishedAt`）；**已装侧从未记录发布时间** |

**根本方法错误**：先设计、后发现工况。本文档即是对该错误的纠正——工况先于设计。

---

## 十、下一步建议

1. **不扩大战线**：撤回那 60 对行为变动的收拢，只保留 `b4878ee`（不拦）——它已被反向测试验证（撤掉修改两个测试即红）。
2. **按矩阵重做设计**：以 I1–I6 为约束、以矩阵为验收标准，先写测试（矩阵每一行一个用例），再写实现。
3. **versionCode 作为第二判据**单独立项（需先解决未决问题 3）。
4. **#934 的 cross-channel 沉默**：不在本 PR 动，但需在 PR 描述里写明"用户如何切换通道"的 UX 缺口。
