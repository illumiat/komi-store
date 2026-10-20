# komi-store 开放 PR 代码评审报告

**工具**：Open Code Review v1.12.7（`@alibaba-group/open-code-review`），provider `deepseek` / model `deepseek-flash`
**范围**：`@illumiat` 名下 **7 个开放 PR**（已关闭的 #967 / #906 按要求排除）
**时间**：2026-09-21，7 个 PR 串行跑完共 **45 分钟**，总消耗 **2497 万 token**（缓存命中率高）

---

## 一、覆盖率（先确认"跑全了"，不是"有输出文件"）

`ocr` 在预算被卡时会写下**部分结果**，所以逐份核对了 `status` / `manifest.terminal_state` / `summary.budget_exceeded`：

| PR | status | terminal_state | 文件 | 评论 | crit | high | med | low | tokens | 耗时 |
|---|---|---|---|---|---|---|---|---|---|---|
| #968 | complete | complete | 11 | 11 | 0 | 0 | 7 | 4 | 5,159,464 | 7m25s |
| #966 | complete | complete | 3 | 1 | 0 | 0 | 1 | 0 | 4,307,212 | 9m22s |
| #964 | complete | complete | 21 | 6 | 0 | 0 | 2 | 4 | 1,594,543 | 3m11s |
| #959 | complete | complete | 13 | 19 | 0 | 0 | 5 | 14 | 3,896,424 | 6m40s |
| #955 | complete | complete | 6 | 6 | 0 | 0 | 4 | 2 | 2,025,551 | 5m58s |
| #954 | complete | complete | 4 | 1 | 0 | 0 | 0 | 1 | 285,337 | 1m1s |
| #934 | complete | complete | 15 | 10 | 0 | 0 | 4 | 6 | 7,697,749 | 11m15s |
| **合计** | | | **73** | **54** | **0** | **0** | **23** | **31** | **24,966,280** | **45m** |

**7/7 全部 `complete`，`budget_exceeded` 均为空** —— 结果完整，无截断。
`--max-tokens 600000`（按 deepseek-flash 的 1M 上下文设定，未用 scan 模板默认的 58888），`--max-tokens-budget` 保持 0。

**没有 critical / high**；54 条中 23 条 medium、31 条 low。

---

## 二、独立复核（技能明确要求：不可照单全收）

对 **23 条 medium 逐条做位置核对**（在各自 PR 的提交树里取真实行内容）：
**23/23 位置准确**，没有出现"引用改动前行号"那类失效。

再对 **8 条**做语义深核（回旧实现对照 / 查测试 / 读调用方），结论如下：

| # | PR | 主张 | 复核结论 |
|---|---|---|---|
| 1 | #968 | `notifySessionExpired()` 清了 token 但没维护会话快照 | ✅ **成立**（`tokenStore.clear()` 在 169 行，函数内 `recordSession`/`clearLastKnownSession` 命中 **0** 次）。**但引用行号 193-196 其实是 `logout` 的** —— 主张对、行号偏 |
| 2 | #968 | `contentScale` 硬编码，调用方无法再影响填充方式 | ⚠️ **不成立**（自我假设式表述）。实测新代码就是 `ContentScale.Crop`，而已确认 Landscapist 的 `ImageOptions` 默认值也是 `Crop` → **无行为变化** |
| 3 | #959 | Feed/Home/Search 三处丢了 `Modifier.animateItem()` | ✅ **成立**（diff 实证：`- Modifier.animateItem()` → `+ Modifier`；且 `FavouritesRoot:152` 在交错网格里确实用了 `animateItem()`，说明 API 可用、修得回来） |
| 4 | #959 | `drivesPullToRefresh()` 在桌面端也装了 | ✅ **成立**（无条件应用；而 `KomiPullToRefresh` 只在 `isAndroid()` 分支里）—— 这处正是我改动时的取舍，曾被记为已知残留 |
| 5 | #955 | gate 只覆盖 5 个源，`appLanguageTag` 在 gate 之外 | ✅ **成立**（`combine(` 只有一处=那 5 个源；`appLanguageTag` 由独立 collector 在第 118 行写入，而它决定字体脚本） |
| 6 | #964 | `containerColor/contentColor` 只有 Primary 变体生效 | ✅ **成立**（覆盖写在 `when (variant)` 的单个分支内，而 `MangaButton` 对所有变体都生效 → 两种人格实现不一致） |
| 7 | #934 | `usedTimestampLogic` 与 `VersionMath.isTimestampTrackedTag` 是两套判断 | ✅ **成立**（后者是既有权威谓词 `opaque \|\| hasHexTail`，前者在 `UpdateVerdict` 里重新推导且条件更多） |
| 8 | #934 | `observeExternalInstall` 不再写 `installedVersion`，导致版本陈旧 | ⚠️ **把设计选择当缺陷**：该行上方注释明写 *"observe zone — system observations, **never** the installedVersion tag"*，且 PR 自带测试 **`observeExternalInstallNeverTouchesInstalledTag()`** 把它钉住 → 是**有意不变式**（分区分写以防跨写者互相覆盖），非疏忽。**后果是否可接受需作者判断，不应静默"修"** |

**复核结论：8 条深核中 2 条不成立/需重判（25%）** —— 与技能里"约 20% 归因不成立"的经验一致。**这也是为什么不能拿 severity 直接排期。**

---

## 三、值得处理的发现（按优先级）

### 优先看（真问题、有明确后果）

1. **#959 `animateItem()` 在三个页面被丢掉** —— `FeedRoot:280`、`HomeRoot:288`、`SearchRoot:602`
   列表项插入/移动/刷新不再有动画（排行变化时最明显），属可见回归，且修复成本低。

2. **#955 首帧 gate 漏了 `appLanguageTag`** —— `MainViewModel.kt:84-89` / `:118`
   gate 等的是 5 个外观源，但语言标签由另一条 collector 写入，而它决定字体脚本 → **首帧可能用错字体的语言**，正是该 PR 想消灭的那类"首帧不对"。**这条和 PR 的目标直接冲突，建议优先。**

3. **#968 会话过期路径不维护快照** —— `UserSessionRepositoryImpl.kt:133-173`（`tokenStore.clear()` 在 169）
   401 过期后 `_lastKnownSession` 仍报 `isLoggedIn=true` 带旧账号；当前只靠 Profile 页的 collector 兜。**该 PR 引入快照就是为了首帧正确，这里留了缺口。**

4. **#964 `KomiButton` 的颜色覆盖只对 Primary 生效** —— `KomiButton.kt:281`
   与 `MangaButton` 行为不一致；将来别的变体要固定色会静默失效。

5. **#966 无底栏时预留的条带没有背景** —— `AppNavigation.kt:163-169`
   唯一一条 medium，值得看一眼实际观感。

### 归纳类（low，建议批量清理）

- **死 import**：#959 `AppsRoot.kt:19` `LazyGridState`、`CategoryListRoot.kt:10` `LazyStaggeredGridState`、`SearchRoot.kt:30` `StaggeredGridItemSpan`
- **import 顺序**（会挂 `ktlintCheck`）：#954 `DetailsWhatsNewViewModel.kt:13`、#934 `PackageEventReceiver.kt:20`、#959 `AppsRoot.kt:73`
- **无意义残留**：#959 `AppsRoot.kt:389` 的 `span = { _, _ -> GridItemSpan(1) }`（等于默认值）、`AppsRoot.kt:353` 的多余 `Box` 包裹、`FeedRoot.kt:190` 的 `align(Alignment.TopCenter)` 失效、#955 `MainViewModel.kt:94` 的 `isCompleted` 冗余守卫
- **重复/可合并**：#959 `ScrollbarContainer.jvm.kt` 的重载与 `GridScrollbarAdapter` 逐行重复；#934 `DetailsViewModel.kt:2489` 与 `RawDetailsStateMapper.computeCanSwitchToStable` 规则重复

### 需要作者判断（不建议直接改）

- #934 `observeExternalInstall` 的"分区不变式"取舍（见复核表第 8 条）
- #955 `catch (e: Exception)` 不覆盖非 `Exception` 的 `Throwable`（与注释承诺不完全一致）
- #934 `ReleaseWindow` 只迁移了 backend / Forgejo，GitHub 直连兜底路径未走同一处（注释里的"每个 forge 源都经此处"尚未成立）

---

## 四、方法与边界（务必知悉）

- **只采信 `ocr` 的"位置"与"现象"，不采信其"归因"**。上面 8 条深核就是执行这一步的结果。
- **本次只深核了 8/54 条**，其余 46 条（含 15 条 medium）**仅做了位置核对，未做语义复核**。若要把某条当真去改，请先按同样方式回旧实现对照。
- **API 级别问题不该交给 LLM**：`ocr` 在这一类上实测会漏（技能记载 15 条 `[NewApi]` 只命中 1 条，且有误报）。Android 侧应以 `gradle :composeApp:lintDebug` 为准 —— 注意 **`assembleDebug` 不触发 lint**。
- 原始证据：`.ocr/pr<N>.json`（逐 PR 完整 JSON，含 `suggestion_code` / `existing_code` 可直接生成 patch）、`.ocr/all-comments.json`（54 条合并）。

## 五、复现命令

```bash
export PATH="$PATH:$(npm config get prefix)"
ocr review --from origin/main --to <PR head sha> \
    --format json --output .ocr/pr<N>.json \
    --audience agent --max-tokens 600000
```
