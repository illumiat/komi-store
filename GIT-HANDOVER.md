# 纯 Git 管理员交接文档

> 来源：komi-store 维护实践沉淀（2026-09），剥离项目业务，只保留 Git/PR 管理方法论
> 面向：任何使用 fork + PR 工作流的仓库管理员

---

## 一、仓库拓扑：双 remote 是基础配置

| 名称 | 角色 | 典型地址模式 |
|---|---|---|
| `origin` | 上游主仓库（无写权限或仅合并权限） | `github.com/<org>/<repo>` |
| `fork` | 个人 fork，**所有 PR 头分支推这里** | `github.com/<you>/<repo>` |

**核心纪律：push 永远指名 remote。** `git push origin <branch>` 在无上游写权限时 403，`git push fork <branch>` 才是正解。模糊的 `git push` 会默认猜 remote，出过错的环境一律显式写。

组织改名注意：GitHub 旧 URL 会自动重定向（web 和 git 均是），但 `gh api` 调用用新路径最稳；gh 命令回显的 URL 可能显示改名后的路径，不影响操作。

---

## 二、分支管理

### 并行 PR 在飞时的纪律

1. **禁止直接切分支改代码**——工作区状态会串到错误的 PR 里。隔离手段是 worktree：
   ```bash
   git worktree add .worktree-<task> fork/<branch>
   # 干活……
   git worktree remove --force .worktree-<task>
   ```
   worktree 不共享未跟踪文件（如构建配置、SDK 指针），**建完先补拷所需配置文件**（Android 项目是 `local.properties`，其他项目自查）。

2. **多 PR 合并验证用本地合并分支**：
   ```bash
   git checkout -b test/combined-xxx origin/main
   git merge --no-ff fork/<pr1-branch>
   git merge --no-ff fork/<pr2-branch>
   ```
   合并分支只做本地验证（编译+测试），**默认不推**。它还有个副产品价值：先合的 PR 不会给后面的制造冲突，说明 PR 间相互独立。

3. PR 改动文件集的重叠检查在合并前做一遍：
   ```bash
   git diff origin/main...fork/<branch> --name-only
   ```
   零交集 = 任意顺序合并都无冲突风险。

### 提交卫生

- **一个提交 = 一个逻辑变更**。重构、修 bug、功能分家提交。
- **未推送的提交可以自由压平**：修正提交改的是还没推的本地提交时，没有历史需要保留，`git reset --soft <base>` 后重新提交，让 reviewer 看到"一次做对"的样子。已推送的提交不要随意改写（评审期 force-push 会被打回）。
- 提交信息：`fix:`/`feat:`/`refactor:` 前缀 + 小写祈使句 + 一句话说清 what，目标 <72 字符。
- **commit 前核对 `git config user.email`**——local config 可能带入他人身份，署名错了事后要 filter-branch 全仓库重写，成本极高。多机器/多身份环境每台机器都查一遍。

---

## 三、PR 流程控制

### 开 PR 前的自查清单

1. 从最新 `origin/main` 切分支（不要从旧基线切）。
2. 单一逻辑变更；长出了就拆栈，不要一锅炖。
3. 本地检查全绿——**没有 CI 兜底的仓库，本地验证就是唯一防线**，别指望 merge 按钮抓构建错误。
4. PR 描述要素齐：What / Why / How to test / 截图（UI 改动强制）/ 关联 issue。

### PR 会被拒的典型原因

- 混合关注点（refactor + feature + 依赖升级一个 PR）
- 新依赖没有理由
- UI 改动没截图
- 违背仓库既有模式
- 空/含糊提交信息、历史留 WIP

### 评审意见处理

- 机器人评审（CodeRabbit 等）：每条评论要么修掉、要么**带着论据驳回**——"LGTM" 式敷衍会被人类评审打回。机器人不总是对的，可证伪地顶回去是正当操作。
- 人类评审：用 follow-up commit 回应，评审期不 force-push（除非评审者要求）。
- 合并通常是维护者的 Squash and merge，分支自动删除——所以评审期提交历史整洁与否不是大问题，但提交粒度影响 review 效率。

---

## 四、GitHub 写操作控制（AI 协作者场景）

如果让 AI 助手参与仓库管理，写权限纪律是安全边界：

1. **未经人确认，AI 禁止任何 GitHub 写操作**（评论、编辑 PR 正文、创建 PR、合并、push）。流程：起草 → 人过目 → 批准 → 执行。这条被违反过多次后确立为铁律——AI 起草的内容质量可能没问题，但**对外发言的授权必须在人手里**。
2. AI 的审查报告只落本地文件，不直接发到 GitHub。
3. push 前同样需要人确认。

### gh CLI 实操要点

- 桌面 GitHub connector 类工具通常只有只读权限（写操作 403），写操作走 gh CLI。
- `gh pr edit <n> --body-file <file>` 在 Bash 里用**相对路径会找不到文件**——用绝对路径，或改用 PowerShell 调完整路径。
- PR 正文修改的标准回路：
  ```bash
  gh api repos/<org>/<repo>/pulls/<n> --jq .body > draft.md   # 导出
  # 编辑 draft.md
  gh pr edit <n> --repo <org>/<repo> --body-file draft.md      # 应用
  gh api repos/<org>/<repo>/pulls/<n> --jq '.updated_at'       # 核实
  ```
  应用后用 `--jq` 核实关键字段（updated_at 变了、旧措辞 grep 不到）——"发出去"不等于"生效了"。

---

## 五、PR 正文与代码的同步纪律

这是实践里最容易翻车的地方：**代码改了，正文没跟上，reviewer 会拿正文当规格挑错。**

1. **PR 正文的 Validation（验证）段必须逐项对应当前 HEAD**：改了实现就重核每条声明。没做过的验证不许写"已验证"；旧版本的验证数据不能替新代码背书。
2. 正文里描述的行为要与代码事实一致——绝对化措辞（"always"、"never"、"impossible"）在演进中容易变成谎言，写的时候留诚实余地（"on the rare timeout path"比"never"活得更久）。
3. 正文自带三类矛盾的可能：代码违约（改码）、正文与代码不符（改正文或改码，取其一）、正文自相矛盾（通常收窄承诺）。

---

## 六、AI 辅助代码评审方法论

以下是经过多轮实测校准的 PR 审查流程，适用于任何仓库：

### 双轴审查

两个维度分开跑、分开报，**不合并不排序**：

- **Standards 轴**——代码是否符合仓库成文规范（CONTRIBUTING/CLAUDE/风格指南）+ 一套固定的坏味道基线（Fowler：Duplicated Code、Data Clumps、Primitive Obsession、Speculative Generality 等，逐条判断而非硬性违规）。
- **Spec 轴**——代码是否忠实兑现 PR 正文/issue 描述：缺失的、多做的（scope creep）、看似实现实则错的。

分轴的原因：规范全对但做错了事的代码、和做对了事但风格全错的代码，混在一起报会互相掩护。

### 盲审操作要点

- 审查子代理（或 AI 会话）只喂 diff 命令 + PR 正文，**不给历史审查结论、不给严重度引导词**。
- **严重度引导语是噪声源**（实测数据）："吹毛求疵级/诋毁级"的提示会让 ~60% 指控是需实弹驳倒的虚报；校准口径（不引导严重度，让审查者自定）噪声近零。
- 每条报告先**实弹核验**再采纳：正则行为写脚本复现、并发时序读源码推演、对照 main 分辨"新引入"还是"既有问题"。AI 审查会虚报——它可能把"防御性冗余"判成"潜在 bug"，把"已完成的延迟初始化"判成"永久滞留"。
- 发现分三类处理：代码违约 → 改码；正文与代码不符 → 改正文或改码（选成本低、语义诚实的那个）；两特性出生时间不同造成的接缝 → 补齐或明示。

### 重构审查的特殊点

重构 PR 的核心不变量是**行为等价**。审查时要求审查者逐 hunk 对比重构前后（`git show <base>:<path>`），专盯：

- 副作用的位置变化（如日志语句移过了一个 filter——被记录的集合就变了，尽管返回值不变）
- 异常处理语义变化（吞掉的异常现在重抛了？这是改进但也是漂移，要明示）
- 陈旧快照问题：读到的 entity 在中途被更新过之后，整行写回会回退刚写入的字段——**列级写 vs 整行写**是高频事故点

### 收敛判据

审查轮次不是越多越好。**单轮 0 硬性违规 + ≤2 判断项 + 0 真问题 → 内部审查收敛停手**，剩余增量留给人工评审——人类评审者看代码的角度（集成面、发布风险）和 AI 被喂的（diff+正文）不同，那是另一批没采样过的区域。

### 深化重构的取舍判据

判断"要不要抽共享模块/下沉类型"时的实测原则：

- 先数**真实引用点**和**迁移后真省的处数**——审查报告常高估重复度（说 4-5 处、实际可省 2 处）；
- 接口面没有变厚的搬家不是深化，是原地踏步；
- 仓库有 minimal-diffs 政策时，"等未来加第 N 个字段再提"通常是正确答案；
- 已有测试钉死的现状（哪怕不完美）优先维持。

---

## 七、验证与发布节奏

1. **本地验证命令分层**：根级 lint → 模块测试 → 受影响模块编译 → 全量打包。记住各层的准确任务名（跨平台项目尤其：JVM 测试不是 `allTests`，Android 编译任务名带 `Debug`/`Android` 后缀）。
2. 功能进 main ≠ 立即发布——release 是独立节奏（bump 版本 → tag → 签名构建 → 手写 release notes），归 release 管理员，贡献者不用碰但要知道存在。
3. 真机/真环境冒烟的最小流程（Android 例）：覆盖安装（签名一致时 `adb install -r` 免卸载）→ `logcat -c` 清缓冲 → 启动 → 查 `pidof` 存活 + `FATAL EXCEPTION` 计数。

---

## 附：速查卡

```bash
# push（指名 remote）
git push fork <branch>

# worktree 隔离
git worktree add .worktree-x fork/<branch> && cp <config> .worktree-x/
git worktree remove --force .worktree-x

# 多 PR 本地合并验证
git checkout -b test/combined origin/main
git merge --no-ff fork/<b1> && git merge --no-ff fork/<b2>

# 文件集重叠检查
git diff origin/main...fork/<branch> --name-only

# PR 正文修改回路
gh api repos/<org>/<repo>/pulls/<n> --jq .body > d.md
gh pr edit <n> --repo <org>/<repo> --body-file d.md
gh api repos/<org>/<repo>/pulls/<n> --jq '.updated_at'

# commit 前署名检查
git config user.email
```
