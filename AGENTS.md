# AGENTS.md — 项目入口（AI 工具会话启动时自动加载）

> 本文件由 pipeline-ops scaffold 生成。完整协议在 `.agents/AGENTS.md`。
> **开工前必读三件套**：`.agents/AGENTS.md`（协议）→ `.agents/STATE.md`（状态）→
> `pipeline_state.json`（权威登记，只读）

## 最小铁律

1. 开工：`git pull` → 读三件套 → 查 `.agents/claims/` → 改共享文件先写认领；
2. 收工：更新 `.agents/STATE.md`（一屏内）→ 追加 `.agents/DECISIONS.md` → 释放 claim → commit + push；
3. 产物 write-once（独立 run 目录，失败保留并标记）；不旁路写权威状态；
4. 新长跑发射前先过纳米预演——nano 的 gate 只查管道完整性，质量阈值仅长预算生效；
5. 重型任务严格串行；轻任务并行需先认领。
