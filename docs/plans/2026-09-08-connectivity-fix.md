# WeGood v1.2.0 修复计划：启动卡死 + 连接方式（联网/蓝牙）

> 2026-09-08 · 由用户真机反馈"App 根本打不开，一直停在进入配对界面之前"触发。
> 本文档是本轮修复的完整方案，实现按此执行，验收门见 §6。

## 1. 问题与根因

### 现象
真机安装 release APK 后，App 永远停在💗加载页，进不去配对界面。

### 根因（代码级，已确认）
1. **启动被网络硬阻塞**（主因）：`MainActivity.AppRoot` 用 `state == null → LoadingScreen` 决定是否进入 UI，
   而 `Repo._state` 只在 `Api.me()` **成功返回**后才会从 null 变为有值。
   首次启动时 `ensureRegistered()` 需要联网注册；服务器不可达 → 抛异常 → `state` 永远为 null → **永远卡在加载页**。
2. **默认服务器地址是模拟器专用**：`Prefs.DEFAULT_SERVER = "http://10.0.2.2:3000"`（10.0.2.2 是 Android 模拟器
   指向宿主机的别名），真机上永远不可达 → 首次安装必然触发根因 1。
3. **本地缓存不回灌**：已配对用户断网/服务器挂掉时同样卡死（Prefs 里有完整快照，但从不用于构造 state）。
4. **连接状态不可见**：用户无法知道"我为什么进不去"，也没有重试入口。

## 2. 联网方案设计（回答"需不需要服务器"）

**结论：远距离互动必须有中转服务器。** 两台手机隔着运营商网络无法可靠直连（NAT/防火墙），
服务端已经在仓库里写好（`server/index.js`，Node + Express + SSE，数据量极小，最低配 1核512M 都够）。
给用户三条落地路径（详见 `docs/DEPLOY.md`）：

| 路径 | 适用 | 成本 | 步骤量 |
|---|---|---|---|
| ① 电脑跑服务端（同一 WiFi） | 在家测试、最快跑通 | 0 | `node server/index.js`，手机填 `http://电脑IP:3000` |
| ② 免费云托管（Render/Railway） | 长距离日常使用（推荐） | 0 | GitHub 仓库一键部署，得到 `https://xxx.onrender.com` |
| ③ 自有 VPS | 最稳定、可控 | ~¥30/月 | node + systemd，可选 Nginx+HTTPS |

App 侧配合改造：启动不再被网络阻塞；"我的"页可改服务器地址并**测试连接**（显示延迟）；
离线时首页显示状态条，每 15 秒自动重试。

## 3. 蓝牙直连设计（近距离补充，用户可自选）

用户明确要求：连接方式可自选（联网 / 蓝牙）。设计如下 MVP：

- **模式开关**：`Prefs.connMode ∈ {net, bt}`，在"我的"页切换；配对页提供两个页签。
- **传输层**：`bt/BtLink.kt`，Bluetooth Classic RFCOMM（SPP），自定义 UUID。
  只使用**系统已配对的设备**（双方先在系统蓝牙设置配对一次），因此**不需要定位权限、不需要扫描**。
  角色对等：双方都可监听（等待连接）/主动连接，谁连上都建立同一条链路。
- **协议**：换行分隔 JSON：`hello`(报姓名) / `heart`(发爱心) / 双方收到即回写本地动态。
- **数据归属**：蓝牙模式承载**核心互动**——发爱心、在线状态、动态流水；
  纪念日/游戏/每日一问需要共同数据存储，仍走互联网模式（UI 明确提示，不做假同步）。
- **配对状态桥接**：蓝牙连上后，若尚未进行服务器配对，则以对方蓝牙名注入"伪 partner"，
  使全部主界面（首页/发爱心/小组件）可用；断开后伪 partner 撤销。
- **生命周期**：App 进程存活期间监听（蓝牙关/权限缺失静默降级），切回联网模式即停。

## 4. 修复清单（文件级）

| 文件 | 动作 | 内容 |
|---|---|---|
| `data/Prefs.kt` | 改 | 新增 `connMode` / `isBtMode` |
| `bt/BtLink.kt` | 新增 | RFCOMM 链路 + BtMsg 协议 + 状态流 + 收件流 |
| `data/Repo.kt` | 改 | 本地快照回灌 `hydrateLocal()`；`Conn` 状态流；蓝牙收发分派；15s 离线看门狗；`testServer()` |
| `data/Api.kt` | 改 | `ping()` 探测（3s 超时，任意 HTTP 响应即视为可达） |
| `MainActivity.kt` | 改 | LoadingScreen 仅作瞬时兜底（回灌后几乎不出现）；文案区分"连接"与"加载" |
| `ui/screens/PairScreen.kt` | 改 | 互联网/蓝牙两页签；离线横幅 + 重试按钮；蓝牙设备列表/等待连接/状态卡 |
| `ui/screens/HomeScreen.kt` | 改 | 顶部连接状态条（联网在线不显示；离线/连接中/蓝牙状态可见） |
| `ui/screens/MeScreen.kt` | 改 | 连接方式切换卡；服务器地址弹窗加"测试连接"；版本号改用 BuildConfig |
| `AndroidManifest.xml` | 改 | `BLUETOOTH_CONNECT`(31+)、`BLUETOOTH/BLUETOOTH_ADMIN`(≤30) |
| `app/build.gradle.kts` | 改 | versionName 1.2.0 / versionCode 3；开启 buildConfig |
| `app/src/test/.../BtMsgTest.kt` | 新增 | BtMsg 序列化契约测试 |
| `docs/DEPLOY.md` | 新增 | 三条服务端部署路径图文步骤 |
| `docs/STRUCTURE.md`、`README.md` | 改 | 新文件说明 + 连接方式章节 |

## 5. 明确不做（本轮）

- 蓝牙模式不承载纪念日/游戏/每日一问（需共同存储，MVP 不做假同步）。
- 不做 BLE/GATT、不做蓝牙后台通知（前台互动已覆盖 MVP）。
- 不做服务器端改动（协议未变）。

## 6. 验收门

1. `testDebugUnitTest` 全绿（原 7 项 + 新增 BtMsgTest）。
2. `assembleDebug` + `assembleRelease` BUILD SUCCESSFUL；apksigner verify 通过。
3. 启动路径代码审查：`state == null` 分支在回灌后不可达；离线也能进配对页/主页。
4. 无法本机验证的项诚实标注为 PASS_WITH_LIMITATIONS：真机蓝牙双机互联手感、云端部署端到端。

## 7. 风险与回滚

- 蓝牙双机场景无法在本机自动化验证 → 以协议测试 + 代码审查兜底，发布说明写清操作步骤。
- 所有改动向后兼容：默认仍是联网模式，老用户升级无感。
- 回滚 = revert 本轮 commit，协议/服务端零变更。
