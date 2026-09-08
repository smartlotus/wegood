# Claim: connectivity-fix（v1.2.0 启动卡死修复 + 连接模式）

- owner: zcode-agent
- 时间: 2026-09-08
- 范围: `app/src/main/java/com/wegood/app/**`（data/Repo、data/Prefs、bt/ 新增、ui/screens/{Pair,Home,Me}、MainActivity、AndroidManifest）、`app/build.gradle.kts`、`docs/`、`runs/`、`pipeline_state.json`（收口时）
- 预计时长: 2~3 小时（单会话内完成）
- 事由: 用户真机反馈 App 卡在加载页进不去；默认服务器地址为模拟器地址真机不可达；需离线可进入、连接状态可见、服务器可配置、新增蓝牙直连模式
- 状态: RELEASED（2026-09-08 23:58，B/C 验收票已出，D 推送随收工 commit 完成）
