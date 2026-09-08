# 服务端部署指南（联网模式怎么跑起来）

> 结论先说：**远距离互动必须有一台中转服务器**（两台手机隔着运营商网络无法可靠直连）。
> WeGood 的服务端就是一个几 MB 的 Node.js 小程序，数据是本地 JSON 文件，两人规模 1 核 512M 都绰绰有余。
> 蓝牙直连模式不需要任何服务器，但只适用于近距离（同一间屋子）。

按你的情况三选一：

---

## 方案 ①（最快跑通，0 成本）：家里电脑当服务器，同一 WiFi 下使用

适合：先跑起来试试、在家一起用。

1. 电脑装 [Node.js](https://nodejs.org)（LTS 版本即可）
2. 在项目 `server/` 目录执行：

   ```bash
   npm install
   set TZ=Asia/Shanghai && node index.js    # Windows CMD
   # macOS/Linux: TZ=Asia/Shanghai node index.js
   ```

3. 查电脑局域网 IP：Windows `ipconfig` 看「IPv4 地址」，如 `192.168.1.23`
4. 两部手机连**同一个 WiFi**，App 配对页 →「服务器设置」→ 填 `http://192.168.1.23:3000` → 保存并重连
5. 防火墙放行 3000 端口（Windows 第一次运行时弹窗选"允许"即可）

局限：出门在外（不同 WiFi）就够不着了 → 需要方案 ② 或 ③。

## 方案 ②（推荐日常使用，0 成本）：免费云托管 Render

适合：异地日常使用，不想买服务器。

1. 把本项目推到你的 GitHub 仓库
2. 注册 [render.com](https://render.com) → New → **Web Service** → 连接你的 GitHub 仓库
3. 配置：
   - Root Directory：`server`
   - Build Command：`npm install`
   - Start Command：`node index.js`
   - Environment Variable：`TZ = Asia/Shanghai`
   - 实例选 Free 即可
4. 部署完成后得到 `https://你的应用名.onrender.com`
5. 两部手机 App 里服务器地址填这个 URL（无需端口号）→ 保存并重连

> Railway.app 步骤几乎一样。免费实例闲置 15 分钟会休眠，首次唤醒多等几秒属正常现象。

## 方案 ③（最稳定）：自己的 VPS

适合：长期稳定使用、想要自己的域名。

```bash
# 阿里云/腾讯云轻量服务器（最低配即可），装 Node 18+
scp -r server/ root@服务器IP:/opt/wegood
ssh root@服务器IP
cd /opt/wegood && npm install
npm i -g pm2
TZ=Asia/Shanghai PORT=3000 pm2 start index.js --name wegood && pm2 save
# 安全组/防火墙放行 3000 端口
```

App 里填 `http://服务器IP:3000`。数据备份 = 拷走 `server/data/db.json`。

---

## 蓝牙直连（近距离免服务器，与上面任一方案叠加可用）

两部手机先在**系统设置 → 蓝牙**里互相配对一次，然后：

1. 两部手机都打开 WeGood → 配对页 →「🔵 蓝牙直连」页签
2. 一方点「等待 TA 连接」（或保持页面打开），另一方在设备列表里点 TA 的手机 → 连接
3. 之后发爱心即时直达；首页顶部显示「蓝牙直连 · 对方名字」

说明：蓝牙模式承载**即时互动**（发爱心、在线状态）；纪念日、游戏、每日一问需要共同数据存储，请切回互联网模式使用。连接方式在「我的 → 连接方式」随时切换。
