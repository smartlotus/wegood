# AGENTS.md — 本项目多 Agent 协作协议

> 任何 AI Agent 开工前必读。读完本文件 + STATE.md + pipeline_state.json 即可开工。

## 三条铁律

1. **开工先读，收工必写**：开工 = `git pull` + 读 STATE.md / pipeline_state.json；收工 = 更新 STATE.md + `git commit` + `git push`。交接靠机制（diff），不靠记忆。
2. **动文件先认领**：改共享代码前在 `.agents/claims/` 写认领文件；发现已被认领就停，不抢。
3. **产物只写 run 目录，状态只有编排器能写**：任何人不准旁路写 `pipeline_state.json`，不准覆盖历史产物。

## 文件职责

| 路径 | 职责 | 谁写 |
|---|---|---|
| `pipeline_state.json` | 权威状态（唯一事实源） | 仅编排器 |
| `.agents/STATE.md` | 给人看的一屏状态快照 | 任何 Agent 收工时 |
| `.agents/DECISIONS.md` | 决策流水（append-only） | 做决策的 Agent |
| `.agents/handoffs/` | 交接文档（一次一签） | 交接发起方 |
| `.agents/claims/` | 文件/领域锁 | 认领方 |
| `.agents/incidents/` | 事故复盘 + 故障交接包 | 处理方 |
| `runs/<run_id>/` | 产物（write-once） | 该 run 的执行者 |

## 流水线环节与验收门（WeGood 定制）

| 环节 | 门（可计算 PASS/FAIL） | 说明 |
|---|---|---|
| A 服务端 | `cd server && npm test` 退出码 0 | 两设备端到端冒烟：注册/配对/SSE/爱心/纪念日/井字棋/每日一问/解绑 |
| B Android Debug | `./gradlew :app:assembleDebug :app:testDebugUnitTest` 退出码 0 | Kotlin 编译 + JUnit 日期计算全绿 |
| C Android Release | `./gradlew :app:assembleRelease` 退出码 0 且 APK 存在 | debug 密钥签名，可直接安装 |
| D 发布 GitHub | `gh repo create` + `git push` 后远端 HEAD==本地 | 含全部文件功能说明（docs/STRUCTURE.md） |

## 纳米预演

改过任何代码后先跑最小预算全链：A 的 nano = `npm test`（本就是秒级）；B/C 的 nano = `./gradlew :app:compileDebugKotlin`。绿了才允许正式 assemble。

## 自主 Agent 动作白名单（长期自主运行必填）

- 允许执行的脚本路径：`./gradlew *`、`node server/index.js`、`node server/test/smoke.js`、`npm install`（限 server 目录）、`python .agents/scripts/*`
- 允许写入目录：`app/`、`server/`、`docs/`、`design/`、`runs/`、`.agents/`（incidents 只增不改）
- 允许 git 动作：`add / commit / push`（本仓库）、`gh repo create wegood`
- 禁止：`push --force`、删除 `runs/` 历史与 `server/data/db.json`、任何应用商店外发动作、修改 `.gitignore` 白名单外的敏感文件

## 工作节奏

开工：pull → 读状态 → 查 claims → 认领 → 干活
收工：更新 STATE.md（一屏内）→ 记决策 → 释放 claim → commit + push
