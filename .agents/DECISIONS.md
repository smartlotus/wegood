# 决策流水（append-only，不改写历史行）

格式：`日期时间 | 决策 | 理由 | 证据/链接`

---
2026-09-07 23:20 | 客户端从 PWA 切换为 Android 原生（Kotlin + Compose + Material3） | 用户指定基于 Android Studio 开发 | docs/superpowers/specs/2026-09-07-wegood-couple-app-design.md
2026-09-07 23:20 | UI 对齐 iOS「健康」App：分组灰底 + 高饱和彩色圆角卡 + 大标题 | 用户指定风格 | docs/design/ui-preview.html
2026-09-07 23:25 | AGP 8.13.2 + Gradle 8.13 + Kotlin 2.1.0 + Compose 1.7.6 | 本机 Gradle 缓存全命中，编译近零下载 | ~/.gradle/caches
2026-09-07 23:25 | 实时通道自建 SSE（前台守护 + WorkManager 15 分钟兜底），不用 FCM | 国内不可依赖 Google 服务 | server/index.js, SseService.kt
2026-09-07 23:26 | 纪念日提醒 = 本地 AlarmManager + 服务端 tick 双保险 | App 被杀/离线仍可靠，去重防双响 | Reminders.kt, server tick
2026-09-07 23:40 | release 用 debug 密钥签名 | 个人分发免配签名，APK 可直接安装 | app/build.gradle.kts
2026-09-07 23:45 | 新增三个可互动桌面小组件 | 用户新需求 | widget/Widgets.kt
2026-09-08 00:05 | repo 结构对齐 pipeline-ops（.agents/ + pipeline_state.json + runs/） | 用户要求自动 Agent 架构 | pipeline_state.json
2026-09-08 01:10 | v1.1.0 动效/UI/小组件优化：数字滚动、迷你爱心爆发、光环扩散、弹跳交互、卡片投影、送达反馈、今天徽章、预览图 | 用户要求针对动效/UI/小组件实际优化 | runs/2026-09-08_ui-polish_001/
| 2026-09-08 | v1.2.0 启动卡死修复策略：本地快照回灌代替网络等待 | state==null 硬阻塞是根因（默认服务器 10.0.2.2:3000 真机不可达，Api.me 永不成功）；离线可用是情侣 App 的底线 | runs/2026-09-08_connectivity-fix_001/gate_report.json |
| 2026-09-08 | 蓝牙直连 MVP 只承载即时互动（爱心/在线），纪念日/游戏留在联网模式 | P2P 无共同存储会做假同步；仅系统已配对设备可免定位权限与扫描，MVP 风险最小 | docs/plans/2026-09-08-connectivity-fix.md §3/§5 |
| 2026-09-08 | 联网方案落地为三条部署路径（局域网/Render 免费/VPS），不内置第三方 BaaS | 用户问"是否必须自建"；零依赖 Node 服务端已在库内，免费托管即可长距离使用 | docs/DEPLOY.md |
