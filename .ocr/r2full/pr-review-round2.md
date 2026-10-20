# PR 修复轮次评审报告（Round 2）

**工具**：Open Code Review v1.12.7 / deepseek-flash　**日期**：2026-09-21
**方式**：全量重审 `origin/main → 修复后新头`

## 一、两轮对比

| PR | 一轮 | 二轮 | 修掉 | 仍存/新增 | 修复提交 |
|---|---|---|---|---|---|
| #954 | 1 | **0** | 1 | **0** | `a858181` |
| #955 | 6 | 6 | 1 | 1 | `9bb6a8a` |
| #966 | 1 | 2 | 0 | 1 | `9eeb08b` |
| #934 | 10 | 11 | 4 | 5 | `002a81a` |
| #964 | 6 | 6 | 3 | 3 | `fb28c7a` |
| **#959** | **19** | **13** | **11** | 5 | `63d80b4` |
| #968 | 11 | 12 | 1 | 2 | `27d8342` |
| **合计** | **54** | **50** | **21** | **17** | |

**构建**：7/7 `assembleDebug` + `compileKotlinJvm` 通过；构建后工作区全部干净（证明提交内容 = 构建内容）。

## 二、17 条"仍存/新增"的定性

### A. 我们的修复引入 —— 2 条（均为技术债，非破坏性）

| # | 位置 | 内容 | 判断 |
|---|---|---|---|
| 15 | `ScrollbarContainer.jvm.kt:235` | `linesPerItem` 取自可见窗口的 span 构成，却套用到全部条目；且乘以的 `avgHeight` 是**每条目**均值而非每行均值 → 窗口内含全宽项时两个因子单位不一致 | **成立**。这是我们新写的启发式（原版是 `ceil(total/lanes)`）。改动动机是修更严重的低估（只看到全宽项时 lanes 退化为 1），属"用近似换掉更差的近似"。数值仅影响滚动条比例，不崩 |
| 13 | `GridColumnsTest.kt:69` | `assertTrue(hugeWidth != Int.MAX_VALUE)` 冗余（前一行 `assertEquals(16, hugeWidth)` 已完全钉住） | **成立**。我们写的测试里的冗余断言，纯质量 |

### B. 误报 —— 2 条（OCR 未看调用点）

| # | 位置 | OCR 主张 | 复核结论 |
|---|---|---|---|
| 11 | `PullToRefreshSource.kt:21` | 桌面端该 strip 会成为鼠标滚轮死区 | **不成立**。桌面端已被守卫挡住：`HomeRoot:238` 用 `if (isAndroid())`，`FeedRoot:200` 在 `if (!isDesktop())` 内（项目只有 android/jvm 两个目标）→ 桌面端根本不装该 modifier。OCR 审的是定义文件，没看调用点 |
| 12 | `AppsRoot.kt:619` | 删掉 `span` 后 `idleApps` 被挤成单列 | **不成立**。#959 原始第 635 行本就是 `span = { _, _ -> GridItemSpan(1) }`，**等于默认值** → 我们的删除是 no-op，行为未变。该布局现象在 PR 头就存在 |

### C. 存量：#934 我们从未碰过的文件 —— 5 条

`git diff` 证实我们对这些文件**改动次数为 0**：

| # | 位置 | 内容 |
|---|---|---|
| 4 | `InstalledAppsRepositoryImpl.kt:287` | `adoptMatchedTag` 无条件写 `isUpdateAvailable = false`；`usedTimestampLogic` 在 `codesAlreadyMatch` 之前求值，二者可同时为真 |
| 5 | `SyncInstalledAppsUseCase.kt:261` | 保留 tag 不动 → `installedVersion` 与 `installedVersionName/Code` 自相矛盾 |
| 6 | `VersionMathTest.kt:16` | 缺"marker + 点号数字后缀"（`beta-1.2.3`）的测试 |
| 8 | `VersionHelper.kt:41` | 无条件 return false 会丢弃真实数字证据（候选 `2.0.2` vs 已删 tag 的 `Alpha4.7.4`） |
| 3 | `InstalledAppUpdatesTest.kt:156` | 连续空行（style；core/domain **无 ktlint**，非闸门问题） |

### D. 存量：PR 原有代码路径 —— 5 条

| # | 位置 | 内容 |
|---|---|---|
| 1 | `MainViewModel.kt:159` | 2000ms 超时与 `LANGUAGE_PREF_READ_TIMEOUT_MS` 常量重复（该常量是 #955 原有的） |
| 14 | `ScrollbarContainer.jvm.kt:238` | 估算未计入 `mainAxisItemSpacing` —— **原始版本（两个 adapter）也都没计**，属沿用 |
| 16 | `UserSessionRepositoryImpl.kt:140` | `getUser()` 在集合开始时捕获 token、网络返回后才写快照；若期间登出/过期，会回写 `isLoggedIn = true`。**该写入在 PR 头第 114 行就有**，我们的改动未引入也未消除该竞态 |
| 17 | `UserSessionRepositoryImpl.kt:67` | `clearLastKnownSession` 契约不符（接口说 "Forgets"，实现写入非空登出快照）。**PR 头逐字相同** |
| 7 | `DetailsViewModel.kt:914` | 类别→releases 映射三处重复（重构建议，#934 自身） |

### E. 存量：#964 的设计问题 —— 2 条（**建议作者重点看**）

`DetailsViewModel.kt` 与 `VersionHelper.kt` 我们**改动次数均为 0**：

| # | 位置 | 内容 |
|---|---|---|
| **10** | `DetailsRoot.kt:272` | **降级安装可能根本装不上**：整条安装链路没有任何地方请求平台允许降级 —— root 后端跑 `pm install -r` 不带 `-d`；Shizuku/Dhizuku 只设 `MODE_FULL_INSTALL`，从不设 allow-downgrade 标志。而 PR 的核心功能就是"让用户选择降级"，若平台不认，该选择会静默失败 |
| 9 | `DetailsViewModel.kt:240` | 新路径把旧版装到已安装应用上，但走的是 `install()` 的**全新安装分支**（`installAsset` 未传 `isUpdate`，保持默认 false）→ 下游 `validateApk(isUpdate = false)` **跳过包名不匹配检查** |

## 三、一条需要实机确认的：#966 的双重内缩

**OCR 第 2 条（`KomiScaffold.kt:84`）部分成立**，我核实到了具体指涉对象：

| 环节 | 状态 |
|---|---|
| `AppNavigation` 的 `navigationBarsPadding()` | **#966 引入**（`origin/main` 没有，PR 头第 167 行有）—— 我们只在其**前面**加了 `background` |
| `KomiScaffold` 排除底部 inset | #966 有意为之，注释说明避免"第二次预留"（影响 content，不影响 bottomBar 槽） |
| `CommentComposer` 的 `.systemBarsPadding()` | **`origin/main` 就有**（第 321 行），#966 未改 |

**推论**：`IssueDetailRoot` 不是底栏页 → `showBottomBar == false` → AppNavigation 对整棵子树加底部 inset；而它的 `CommentComposer`（在 `KomiScaffold` 的 bottomBar 槽里）**又**加了一次 `systemBarsPadding()`（含底部）→ **底部可能出现双重内缩（多出一段空白）**。

**归属**：这是 **#966 自身引入的**，与我们的修复无关（我们只加了背景色）。
**状态**：静态分析成立，**未实机确认**。用户此前确认的是"内容被裁成固定带"已修复，未专门验评论输入框。

## 四、总数

| 类别 | 条数 |
|---|---|
| 我们引入（技术债，非破坏） | **2** |
| 误报 | **2** |
| 存量（未碰的文件 / PR 原有代码） | **12** |
| #966 自身引入（需实机确认） | **1** |

**结论**：第二轮**没有发现我们引入的破坏性问题**。两条真引入都是低风险技术债；值得作者处理的是 E 组的 #10（降级安装可能静默失败）与第三节的 #966 双重内缩。

## 五、证据文件

- 第一轮：`.ocr/pr<N>.json`（54 条）
- 第二轮：`.ocr/r2full/pr<N>.json`（50 条）
- 17 条明细：`.ocr/r2full/leftover.json`
