# 项目状态（唯一给人看的状态页）

> 规则：只反映"现在"，一屏以内。过时内容移入 `.agents/handoffs/` 归档。
> 最后更新：2026-09-08 23:58 by zcode-agent

## 现在

- 空闲。v1.2.0（启动卡死修复 + 互联网/蓝牙双连接模式）A/B/C/D 全链收口，远端 HEAD f716132 校验一致。
- 修复要点：本地快照回灌（断网/无服务器也能进界面）、连接状态条、离线看门狗 15s 自动重试、
  服务器地址可改+测试连接、蓝牙直连 RFCOMM MVP、docs/DEPLOY.md 三条服务端部署路径。

## 流水线全景

| 环节 | 状态 | run_id | 验收票 |
|---|---|---|---|
| A 服务端 | PASS | 2026-09-07_server-smoke_001 | npm test 退出码 0（v1.2.0 协议零改动复用） |
| B Android Debug+单测 | PASS | 2026-09-08_connectivity-fix_001 | gate_report（单测 10/10） |
| C Android Release | PASS_WITH_LIMITATIONS | 2026-09-08_connectivity-fix_001 | gate_report（v1.2.0 已验签；蓝牙双机真机项未实测） |
| D GitHub 发布 | PASS | 2026-09-08_connectivity-fix_001 | ls-remote == HEAD f716132（HTTPS 抖动，SSH 通道完成） |

## 下一步

用户侧：装 dist/WeGood-v1.2.0-release.apk；按 docs/DEPLOY.md 选一条服务端路径（或先用蓝牙直连）。
可迭代：真机蓝牙走查、Glance 小组件重写、更多游戏。
