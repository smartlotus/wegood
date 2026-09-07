# WeGood 情侣互动应用 — 设计文档（v2，Android 版）

日期：2026-09-07 ｜ 状态：按用户最新指示从 PWA 切换为 **Android Studio 原生 APP**

## 1. 需求回顾

情侣远程互动应用：配对码绑定/解绑、发送爱心（对方收通知+动画）、内置小游戏、纪念日/倒计时（自定义名称日期、双方可见、天数计算、自定义提前提醒）、通知权限。

## 2. 技术选型（v2）

| 层 | 选型 | 理由 |
|---|---|---|
| 客户端 | **Android 原生（Kotlin + Jetpack Compose + Material3）** | 用户指定基于 Android Studio 开发；本机已装 SDK（android-36.1/build-tools 36.1），可编译验证 |
| 同步服务端 | Node.js + Express + SSE + JSON 文件存储 | 双机数据同步必需一个在线服务；零原生依赖，一台 VPS 即可部署 |
| 实时通道 | SSE（前台/守护服务）+ 15 分钟 WorkManager 轮询兜底 | 国内无法依赖 FCM；SSE 自建通道稳定可控 |
| 本地提醒 | AlarmManager（精确闹钟，带降级）+ NotificationChannel | 纪念日提醒不依赖服务端在线，国内可靠 |

minSdk 26 / targetSdk 35 / compileSdk 35；AGP 8.7.3 + Kotlin 2.0.21 + Compose BOM。

## 3. UI 设计规范（模仿 iOS「健康」App）

设计依据：MacStories《Health in iOS 13》与 Apple HIG 的拆解 —— 健康App 的签名风格是：**浅灰分组背景（#F2F2F7）+ 大标题 + 每类数据一张高饱和度彩色圆角卡片（白字、左上角白色半透明圆形图标位）+ 白色列表卡片（左侧彩色图标圆片）+ 折叠式详情卡片**。

### 3.1 色彩系统（iOS 系统色，高饱和高对比）

| 语义 | 颜色 | 用途 |
|---|---|---|
| 爱心/互动 | #FF2D55（iOS Pink） | 发送爱心、互动卡片 |
| 在一起 | 渐变 #FF6482→#FF2D55 | 首页「在一起第 N 天」英雄卡 |
| 纪念日 | #FF9500（iOS Orange） | 纪念日模块 |
| 游戏 | #5E5CE6（iOS Indigo） | 游戏模块（井字棋 #AF52DE 紫、每日一问 #30B0C7 青、真心话 #FF9F0A 黄） |
| 提醒 | #30D158（iOS Green） | 提醒/在线状态 |
| 页面背景 | #F2F2F7 | 分组背景 |
| 卡片 | #FFFFFF，圆角 20dp（大卡）/16dp（小卡） | |
| 主文本 | #000000 / 次要 #8E8E93（iOS gray） | |

### 3.2 组件

- `MetricCard`：彩色圆角卡，左上圆形半透明白底图标，中部大数字（34-48sp 加粗），底部说明文字
- `ListCard`：白底圆角卡，左 36dp 彩色圆片+白图标，标题 17sp semibold，右侧数值/箭头
- `SectionHeader`：20sp 加粗标题 + 可选右侧动作
- 大标题导航（34sp/800），底部 TabBar（白底、模糊感、4 Tab：首页/纪念日/游戏/我的）

### 3.3 页面结构（对应健康 App「摘要」页）

1. **首页（摘要）**：大标题「WeGood」+ 在一起英雄卡（渐变）+「发送爱心」大按钮卡 + 表情快捷行（❤️😘🤗🌹💦）+ 最近互动列表（ListCard）
2. **纪念日**：橙色系卡片列表（每张显示名称/日期/已过或剩余天数），FAB 新增，底部弹层编辑（名称/日期/每年重复/提前提醒天数/提醒时刻）
3. **游戏**：三张彩色入口卡（井字棋/每日一问/真心话大冒险），各自独立页
4. **我的**：头像位+昵称、配对状态卡（对方在线绿点）、通知设置、实时守护开关、解绑
5. **配对页**（未配对时全屏）：大字我的配对码 + 输入对方码
6. **爱心特效**：收到爱心时全屏爱心粒子升腾动画 + 震动 + heads-up 通知（后台）

## 4. 应用图标设计

向 iOS 健康 App 致敬：**白色（微暖）圆角方底 + 双爱心图形**——后爱心为浅粉（#FF87A3→#FF6482 渐变），前爱心带 iOS Health 同款粉红渐变（#FF6482→#FF2D55），轻微旋转交叠表达"两个人"。实现为 Android Adaptive Icon（前景矢量渐变 + 白色背景 + monochrome 单色层），另附 `design/icon.svg` 矢量源与预览。

## 5. 架构与数据流

```
app/ (Kotlin+Compose)
  data/    Prefs(本地凭证) · Api(REST) · SseClient(实时) · Repo(状态流)
  notify/  Notifications · ReminderScheduler(AlarmManager) · ReminderReceiver · SseService(前台守护)
  ui/      theme · components · screens(Pair/Home/Anniversary/Games/Me) · HeartBurst
server/    index.js(REST+SSE+tick) · store.js · dates.js · test/smoke.js
```

- 双方操作 → 服务端落库 → SSE 广播 → 对方 UI 实时刷新/通知
- 纪念日变更后客户端用 AlarmManager 重排本地提醒（服务器 tick 同步发应用内提醒，双保险去重：SSE 只做应用内横幅，系统通知走本地闹钟）

## 6. 数据模型（服务端）

`devices{id,secret,name,code,coupleId,online}`、`couples{id,members,createdAt}`、`anniversaries{id,coupleId,name,date,repeatYearly,remindDaysBefore,remindTime,createdBy,createdAt}`、`events`(动态流 cap50)、`tttGames`、`dailyAnswers`、`remindersSent`(去重)。

## 7. 测试策略

- 服务端：`test/smoke.js` 端到端（注册→配对→爱心→纪念日→井字棋→解绑，含 SSE 断言）+ dates 纯函数单测 —— 本机可执行
- 客户端：`gradlew assembleDebug` 编译验证（本机 SDK 齐全）；逻辑（天数计算）单元测试放在 `app/src/test`

## 8. 边界与错误处理

- 配对码错误/重复配对/自配 → 服务端明确错误码，UI 提示
- SSE 断线 3s 自动重连并全量刷新；服务被杀后 WorkManager 15min 兜底拉取漏掉的爱心并通知
- 精确闹钟权限被拒 → 降级 setWindow；通知权限 API33+ 运行时申请
- 解绑确认后：删除 couple 及其纪念日/游戏数据，双方收到 unpaired 事件清空本地
