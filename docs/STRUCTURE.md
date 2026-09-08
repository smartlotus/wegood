# WeGood 仓库结构 · 逐文件功能说明

> 本仓库按 **pipeline-ops** 自动 Agent 架构组织：`.agents/` 协作区 + `pipeline_state.json` 权威状态 + `runs/` 写一次产物目录 + 逐环验收门。四个环节：A 服务端 → B Android Debug+单测 → C Release APK → D GitHub 发布。

## 根目录

| 文件 | 功能 |
|---|---|
| `README.md` | 项目总览：部署步骤、配对流程、功能清单、FAQ |
| `settings.gradle.kts` | Gradle 设置：仓库源（google/mavenCentral）、包含 `:app` 模块 |
| `build.gradle.kts` | 根构建脚本：声明 AGP 8.13.2 / Kotlin 2.1.0 / Compose / Serialization 插件版本 |
| `gradle.properties` | Gradle 全局参数（JVM 内存、AndroidX、并行、缓存） |
| `gradle/wrapper/*` | Gradle Wrapper（8.13）：任何人克隆后无需装 Gradle 即可构建 |
| `gradlew` / `gradlew.bat` | Wrapper 启动脚本（Linux/macOS 与 Windows） |
| `local.properties` | 本机 SDK 路径（不入库，gitignore） |
| `pipeline_state.json` | **权威流水线状态**：四个环节的 status/run_id/验收票路径（仅编排器可写） |
| `.gitignore` | 排除构建产物、node_modules、服务端数据、本机配置 |

## server/ — 同步服务端（Node.js）

| 文件 | 功能 |
|---|---|
| `server/index.js` | 服务主体：REST API（注册/配对/解绑/爱心/纪念日 CRUD/井字棋/每日一问）+ `/api/stream` SSE 实时推送 + 每分钟纪念日提醒 tick + 在线状态 presence |
| `server/store.js` | JSON 文件存储（`data/db.json`，tmp+rename 原子写），两人规模零依赖 |
| `server/dates.js` | 日期纯函数库：天数计算、每年重复的下一次日期、提醒触发判定、去重键（供 index 与测试共用） |
| `server/package.json` | 依赖仅 `express`；`npm start` / `npm test` 脚本 |
| `server/test/smoke.js` | **环节 A 验收门**：端到端冒烟——模拟两台设备 + SSE 客户端，覆盖注册→配对→爱心→纪念日同步→井字棋胜负→每日一问互看→解绑清空，含日期纯函数断言 |

## app/ — Android 客户端（Kotlin + Jetpack Compose）

### 清单与资源

| 文件 | 功能 |
|---|---|
| `app/build.gradle.kts` | 应用模块构建：minSdk 26 / target 35、Compose、依赖清单、release 用 debug 密钥签名（可直接安装） |
| `app/proguard-rules.pro` | 混淆保留规则（kotlinx.serialization 数据类） |
| `app/src/main/AndroidManifest.xml` | 权限（网络/通知/精确闹钟/前台服务/开机自启）与组件声明：MainActivity、SseService、提醒与开机广播、**三个小组件 Provider + 互动动作接收器** |
| `res/drawable/ic_launcher_foreground.xml` | **应用图标前景**：双爱心（浅粉后心 + iOS Health 同款粉红渐变前心、白描边） |
| `res/drawable/ic_launcher_monochrome.xml` | 主题图标（Monochrome）单爱心层 |
| `res/mipmap-anydpi-v26/ic_launcher*.xml` | 自适应图标组装（暖白底 + 前景 + 单色层） |
| `res/drawable/ic_stat_heart.xml` | 系统通知小图标（白色爱心） |
| `res/drawable/widget_bg_pink/orange/card.xml`、`widget_btn_circle.xml`、`widget_dot_*.xml` | 小组件背景（粉/橙渐变圆角、白卡描边）、半透明互动按钮、在线状态点 |
| `res/drawable/widget_heart_white.xml`、`widget_preview_*.xml` | 小组件选择器预览图（渐变底 + 爱心 layer-list） |
| `res/layout/widget_love_card.xml` | 情侣卡小组件布局：在一起天数 + 在线点 + ❤️😘🤗 三个直发按钮 + 最近动态行 |
| `res/layout/widget_heart_button.xml` | 一键爱心小组件布局：大爱心 + "点一下，想你了" + 今日计数 |
| `res/layout/widget_anniversary.xml` | 纪念日小组件布局：名称 + 大数字天数 + 日期 |
| `res/xml/*_widget_info.xml` | 三个小组件的元数据（尺寸、30 分钟自刷新、预览图） |
| `res/values/*` | 应用名、颜色、主题（透明状态栏 + 浅灰窗口底色） |

### Kotlin 源码（com.wegood.app）

| 文件 | 功能 |
|---|---|
| `WeGoodApp.kt` | Application：初始化 Prefs、创建通知渠道、启动 Repo |
| `MainActivity.kt` | 单 Activity 入口：导航（底部 4 Tab + 游戏详情页）、通知权限申请、前台标记、UI 事件收集、爱心全屏特效挂载、震动；v1.1.0：底栏图标选中弹跳、💗 心跳脉冲加载页 |
| `data/Models.kt` | 全部数据模型（与后端 JSON 对齐）+ SSE 事件 + 表情映射 |
| `data/Prefs.kt` | SharedPreferences：设备凭证、配对快照（小组件/开机重排提醒离线可用）、今日爱心计数、最近发送时间戳（小组件"已送达"反馈）、实时守护开关；v1.2.0：连接方式 `connMode`（net/bt） |
| `data/DateMath.kt` | 客户端日期纯函数（与 server/dates.js 同语义）：天数、周年滚动、提醒触发时刻；含 `Calendar` 清零陷阱注释 |
| `data/Api.kt` | OkHttp REST 客户端（kotlinx.serialization），设备头鉴权，SSE 专用无超时 client；v1.2.0：`ping()` 3s 短超时探测（任意 HTTP 响应即算可达） |
| `data/Repo.kt` | **中央仓库**：状态流 + SSE 长连接（断线 3s 重连）+ 全部操作（配对/爱心/纪念日/游戏）+ 状态变化分发（小组件刷新/提醒重排/漏发爱心补偿）；v1.2.0 修复启动卡死：**本地快照回灌 `hydrateLocal()`（断网也能立即进界面）**、`Conn` 连接状态流、15s 离线看门狗自动重试、蓝牙/联网分派 `sendHeart`、`testServer()` 测延迟、`setConnMode()` 模式切换、蓝牙消息并入同一事件流（全屏爱心特效复用） |
| `bt/BtLink.kt` | **v1.2.0 蓝牙直连链路（RFCOMM/SPP）**：只依赖系统已配对设备（免定位权限、免扫描）；双端对等（监听等待/主动连接皆可）；换行分隔 JSON 协议 `BtMsg(hello/heart)`；状态流 Off/Waiting/Connected + 收件流；断线自动回到等待态 |
| `notify/Notifications.kt` | 三类通知渠道（互动/提醒/守护）与通知构建（点击回 App） |
| `notify/Reminders.kt` | ReminderScheduler（AlarmManager 精确闹钟，S+ 降级 setWindow）+ ReminderReceiver（触发通知+按年续排）+ BootReceiver（开机重排+小组件刷新） |
| `notify/SseService.kt` | 实时守护前台服务：后台保持 SSE，收到爱心/提醒即刻系统通知 |
| `work/SyncWorker.kt` | WorkManager 15 分钟兜底轮询：App 被杀/守护关闭时补拉漏掉的互动并通知 |
| `widget/Widgets.kt` | **三个小组件 Provider**（情侣卡/一键爱心/纪念日）+ WidgetsUpdater（状态变化统一刷新）+ WidgetActionReceiver（**小组件上直接发送互动**）；v1.1.0：发送后 60s「已送达 ✓」反馈、纪念日当天「就是今天 🎉」徽章、动态 💬 前缀 |
| `ui/theme/Theme.kt` | iOS 系统色板 + Material3 主题（粉 #FF2D55 主色、分组灰底） |
| `ui/components/Components.kt` | 健康风组件库：LargeTitle、SectionHeader、MetricCard（彩色渐变指标卡+柔和投影）、ListCard（彩色图标圆片+右箭头）、OnlineDot、ReactionRow（点按弹跳）、无涟漪点击扩展 |
| `ui/HeartBurst.kt` | 收到爱心的全屏特效：中心光环扩散 + 大爱心弹簧入场叠加脉动 + 14 颗随机心形粒子升腾 + 自动关闭 |
| `ui/screens/PairScreen.kt` | 配对页 v1.2.0：**双页签「🌐 互联网配对 / 🔵 蓝牙直连」**。互联网页签：我的配对码大字展示/复制、离线横幅+点此重试（不再无限转圈）、输入对方码绑定、服务器设置；蓝牙页签：状态卡（未连接/等待中/已连接）、已配对设备列表一键连接、蓝牙权限申请、模式说明 |
| `ui/screens/HomeScreen.kt` | 首页（健康「摘要」风）：在一起英雄卡（天数 0→N 滚动动画）、发送爱心大按钮（弹簧缩放+迷你爱心粒子爆发）、分时段问候语、表情快捷条（点按弹跳）、最近互动流；v1.2.0：顶部**连接状态条**（离线/连接中/蓝牙状态可见，在线不显示保持干净） |
| `ui/screens/AnniversaryScreen.kt` | 纪念日列表（橙色渐变卡：还有/已经 N 天）+ FAB + 底部抽屉编辑器（名称/日期选择器/每年重复/提前提醒档位/时刻） |
| `ui/screens/GamesScreen.kt` | 游戏大厅 + 三个游戏：双人井字棋（实时同步/胜负/落子缩放入场/再来一局）、每日一问（互看规则）、真心话大冒险（内置题库） |
| `ui/screens/MeScreen.kt` | 我的：昵称/配对码、TA 的在线状态卡、通知与精确闹钟权限引导、实时守护开关、重排提醒、解绑确认；v1.2.0：**连接方式切换卡（🌐 互联网 / 🔵 蓝牙）**、服务器地址弹窗加「测试连接」（显示延迟）、版本号改用 BuildConfig |
| `app/src/test/.../DateMathTest.kt` | **环节 B 验收门**：日期纯函数 JUnit（当天即第 1 天、周年滚动、提醒触发时刻、相对时间） |
| `app/src/test/.../BtMsgTest.kt` | **v1.2.0**：蓝牙协议序列化契约测试（往返一致、默认值、未知字段前向兼容） |

## docs/ 与 design/

| 文件 | 功能 |
|---|---|
| `docs/superpowers/specs/2026-09-07-wegood-couple-app-design.md` | 设计文档 v2：需求、技术选型、UI 规范、图标设计、架构、测试策略 |
| `docs/design/ui-preview.html` | UI/图标/小组件设计预览页（浏览器打开，三屏 + 小组件 + 色板） |
| `docs/DEPLOY.md` | **v1.2.0 服务端部署指南**：电脑局域网（最快跑通）/ Render 免费云托管（推荐）/ VPS 三条路径 + 蓝牙直连操作步骤 |
| `docs/plans/2026-09-08-connectivity-fix.md` | v1.2.0 修复计划：启动卡死根因分析、联网方案设计、蓝牙直连设计、验收门 |
| `design/icon.svg` | 应用图标矢量源（与 adaptive icon 同源设计） |
| `docs/STRUCTURE.md` | 本文档：逐文件功能说明 |

## .agents/ — pipeline-ops 协作区

| 文件 | 功能 |
|---|---|
| `.agents/AGENTS.md` | 协议入口：三条铁律、环节验收门、纳米预演、动作白名单、开工/收工流程 |
| `.agents/STATE.md` | 一屏状态快照（现在/全景/下一步） |
| `.agents/DECISIONS.md` | 决策流水（append-only） |
| `.agents/claims/` | 文件/领域认领锁 |
| `.agents/handoffs/` `incidents/` `templates/` | 交接、事故复盘、模板 |
| `runs/<run_id>/` | 构建产物 manifest 与验收票 gate_report.json（write-once） |
