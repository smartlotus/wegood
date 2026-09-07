// WeGood 同步服务端：REST API + SSE 实时推送 + 纪念日提醒 tick
const express = require('express');
const crypto = require('crypto');
const store = require('./store');
const D = require('./dates');

const PORT = process.env.PORT || 3000;
const TICK_MS = Number(process.env.TICK_MS || 30000);
const app = express();
app.use(express.json());

store.load();
const db = store.db;

// ---------- 工具 ----------
const CODE_CHARS = 'ABCDEFGHJKLMNPQRSTUVWXYZ23456789'; // 去除易混淆 0O1I
function genCode(len = 6) {
  let s;
  do {
    s = Array.from({ length: len }, () => CODE_CHARS[crypto.randomInt(CODE_CHARS.length)]).join('');
  } while (Object.values(db.devices).some((d) => d.code === s));
  return s;
}
const uid = () => crypto.randomUUID();

function save() { store.save(); }

function auth(req, res, next) {
  const id = req.header('x-device-id');
  const secret = req.header('x-device-secret');
  const dev = id && db.devices[id];
  if (!dev || dev.secret !== secret) return res.status(401).json({ error: '未登录或凭证无效' });
  req.device = dev;
  next();
}

function partnerOf(device) {
  if (!device.coupleId) return null;
  const c = db.couples[device.coupleId];
  if (!c) return null;
  return db.devices[c.members.find((m) => m !== device.id)];
}

function pushEvent(deviceId, event) {
  const conns = sseConnections.get(deviceId);
  if (!conns) return;
  const payload = `data: ${JSON.stringify(event)}\n\n`;
  for (const res of conns) { try { res.write(payload); } catch { /* 忽略单个连接失败 */ } }
}

function pushToCouple(coupleId, event, exceptId = null) {
  const c = db.couples[coupleId];
  if (!c) return;
  for (const m of c.members) if (m !== exceptId) pushEvent(m, event);
}

function addEvent(coupleId, ev) {
  db.events.push({ id: uid(), coupleId, ts: Date.now(), ...ev });
  if (db.events.length > 200) db.events = db.events.slice(-50);
  save();
}

// ---------- 注册 / 配对 ----------
app.post('/api/register', (req, res) => {
  const name = (req.body && typeof req.body.name === 'string' && req.body.name.trim().slice(0, 20)) || '宝贝';
  const device = {
    id: uid(), secret: crypto.randomBytes(24).toString('hex'),
    name, code: genCode(), coupleId: null, online: false, createdAt: Date.now(),
  };
  db.devices[device.id] = device;
  save();
  res.json({ deviceId: device.id, secret: device.secret, code: device.code, name: device.name });
});

app.get('/api/me', auth, (req, res) => {
  const d = req.device;
  const partner = partnerOf(d);
  const couple = d.coupleId ? db.couples[d.coupleId] : null;
  const anniversaries = d.coupleId
    ? Object.values(db.anniversaries).filter((a) => a.coupleId === d.coupleId)
    : [];
  const events = db.events.filter((e) => e.coupleId === d.coupleId).slice(-30);
  res.json({
    device: pub(d),
    partner: partner ? pub(partner) : null,
    couple: couple ? { id: couple.id, createdAt: couple.createdAt } : null,
    anniversaries,
    events,
    ttt: d.coupleId ? db.tttGames[d.coupleId] || null : null,
    daily: d.coupleId ? dailyPayload(d) : null,
  });
});

app.post('/api/me', auth, (req, res) => {
  const name = req.body && typeof req.body.name === 'string' && req.body.name.trim().slice(0, 20);
  if (!name) return res.status(400).json({ error: '昵称不能为空' });
  req.device.name = name;
  save();
  if (req.device.coupleId) pushToCouple(req.device.coupleId, { type: 'partner_updated' }, req.device.id);
  res.json({ device: pub(req.device) });
});

app.post('/api/pair', auth, (req, res) => {
  const code = (req.body && req.body.code || '').trim().toUpperCase();
  if (req.device.coupleId) return res.status(409).json({ error: '当前设备已配对，请先解绑' });
  if (code === req.device.code) return res.status(400).json({ error: '不能和自己配对哦' });
  const target = Object.values(db.devices).find((d) => d.code === code);
  if (!target) return res.status(404).json({ error: '配对码不存在，请检查' });
  if (target.coupleId) return res.status(409).json({ error: '对方已被其他设备绑定' });

  const couple = { id: uid(), members: [req.device.id, target.id], createdAt: Date.now() };
  db.couples[couple.id] = couple;
  req.device.coupleId = couple.id;
  target.coupleId = couple.id;
  db.tttGames[couple.id] = newTtt(couple.members);
  save();

  const ev = { type: 'paired', coupleId: couple.id, myName: target.name, partnerName: req.device.name, ts: Date.now() };
  pushEvent(req.device.id, { ...ev, myName: req.device.name, partnerName: target.name });
  pushEvent(target.id, ev);
  addEvent(couple.id, { kind: 'sys', text: '绑定成功，开始记录你们的日子吧 ❤️' });
  res.json({ ok: true });
});

app.post('/api/unpair', auth, (req, res) => {
  const coupleId = req.device.coupleId;
  if (!coupleId) return res.status(400).json({ error: '当前未配对' });
  const couple = db.couples[coupleId];
  pushToCouple(coupleId, { type: 'unpaired', ts: Date.now() });
  for (const m of couple.members) if (db.devices[m]) db.devices[m].coupleId = null;
  delete db.couples[coupleId];
  for (const [id, a] of Object.entries(db.anniversaries)) if (a.coupleId === coupleId) delete db.anniversaries[id];
  delete db.tttGames[coupleId];
  delete db.dailyAnswers[coupleId];
  db.events = db.events.filter((e) => e.coupleId !== coupleId);
  save();
  res.json({ ok: true });
});

// ---------- 爱心 ----------
const HEART_KINDS = ['heart', 'kiss', 'hug', 'rose', 'miss'];
app.post('/api/hearts', auth, (req, res) => {
  if (!req.device.coupleId) return res.status(400).json({ error: '尚未配对' });
  const kind = HEART_KINDS.includes(req.body && req.body.kind) ? req.body.kind : 'heart';
  pushToCouple(req.device.coupleId, { type: 'heart', kind, fromName: req.device.name, ts: Date.now() });
  addEvent(req.device.coupleId, { kind: 'heart', heartKind: kind, fromName: req.device.name });
  res.json({ ok: true });
});

// ---------- 纪念日 ----------
function validAnn(body) {
  if (!body || !/^[\s\S]{1,20}$/.test(body.name || '')) return null;
  if (!/^\d{4}-\d{2}-\d{2}$/.test(body.date || '')) return null;
  const rdb = Number(body.remindDaysBefore);
  if (body.remindDaysBefore != null && (!Number.isInteger(rdb) || rdb < 0 || rdb > 30)) return null;
  if (body.remindTime && !/^\d{2}:\d{2}$/.test(body.remindTime)) return null;
  return {
    name: body.name.trim(), date: body.date,
    repeatYearly: !!body.repeatYearly,
    remindDaysBefore: body.remindDaysBefore == null ? null : rdb,
    remindTime: body.remindTime || null,
  };
}

app.get('/api/anniversaries', auth, (req, res) => {
  res.json({ items: Object.values(db.anniversaries).filter((a) => a.coupleId === req.device.coupleId) });
});

app.post('/api/anniversaries', auth, (req, res) => {
  if (!req.device.coupleId) return res.status(400).json({ error: '尚未配对' });
  const v = validAnn(req.body);
  if (!v) return res.status(400).json({ error: '名称或日期格式不正确' });
  const a = { id: uid(), coupleId: req.device.coupleId, createdBy: req.device.id, createdAt: Date.now(), ...v };
  db.anniversaries[a.id] = a;
  save();
  pushToCouple(a.coupleId, { type: 'anniversary_changed', ts: Date.now() }, req.device.id);
  addEvent(a.coupleId, { kind: 'sys', text: `新增纪念日「${a.name}」` });
  res.json({ item: a });
});

app.put('/api/anniversaries/:id', auth, (req, res) => {
  const a = db.anniversaries[req.params.id];
  if (!a || a.coupleId !== req.device.coupleId) return res.status(404).json({ error: '纪念日不存在' });
  const v = validAnn(req.body);
  if (!v) return res.status(400).json({ error: '名称或日期格式不正确' });
  Object.assign(a, v);
  save();
  pushToCouple(a.coupleId, { type: 'anniversary_changed', ts: Date.now() }, req.device.id);
  res.json({ item: a });
});

app.delete('/api/anniversaries/:id', auth, (req, res) => {
  const a = db.anniversaries[req.params.id];
  if (!a || a.coupleId !== req.device.coupleId) return res.status(404).json({ error: '纪念日不存在' });
  delete db.anniversaries[a.id];
  save();
  pushToCouple(a.coupleId, { type: 'anniversary_changed', ts: Date.now() }, req.device.id);
  res.json({ ok: true });
});

// ---------- 游戏：井字棋 ----------
function newTtt(members) {
  return { board: Array(9).fill(null), turn: 'X', winner: null, winLine: null,
    players: { [members[0]]: 'X', [members[1]]: 'O' }, updatedAt: Date.now() };
}
const WINS = [[0,1,2],[3,4,5],[6,7,8],[0,3,6],[1,4,7],[2,5,8],[0,4,8],[2,4,6]];

app.post('/api/games/ttt/move', auth, (req, res) => {
  const g = db.tttGames[req.device.coupleId];
  if (!g) return res.status(400).json({ error: '尚未配对' });
  const idx = Number(req.body && req.body.index);
  const my = g.players[req.device.id];
  if (!Number.isInteger(idx) || idx < 0 || idx > 8) return res.status(400).json({ error: '无效落子' });
  if (g.winner) return res.status(409).json({ error: '对局已结束' });
  if (g.turn !== my) return res.status(409).json({ error: '还没轮到你' });
  if (g.board[idx]) return res.status(409).json({ error: '这里已经有子了' });
  g.board[idx] = my;
  const line = WINS.find((l) => l.every((i) => g.board[i] === my));
  if (line) { g.winner = my; g.winLine = line; }
  else if (g.board.every(Boolean)) { g.winner = 'draw'; }
  else { g.turn = my === 'X' ? 'O' : 'X'; }
  g.updatedAt = Date.now();
  save();
  pushToCouple(req.device.coupleId, { type: 'ttt', ttt: g, ts: Date.now() }, req.device.id);
  res.json({ ttt: g });
});

app.post('/api/games/ttt/reset', auth, (req, res) => {
  const g = db.tttGames[req.device.coupleId];
  if (!g) return res.status(400).json({ error: '尚未配对' });
  Object.assign(g, newTtt(db.couples[req.device.coupleId].members));
  save();
  pushToCouple(req.device.coupleId, { type: 'ttt', ttt: g, ts: Date.now() }, req.device.id);
  res.json({ ttt: g });
});

// ---------- 游戏：每日一问 ----------
const QUESTIONS = [
  '第一次见面时，你对 TA 的第一印象是什么？','你觉得对方身上最孩子气的一刻是什么时候？',
  '如果只能带一样东西去荒岛，你会带 TA 的什么物品？','你最想和 TA 一起重看的一部电影是？',
  'TA 做过让你最感动的一件小事？','你最喜欢 TA 做的哪道菜？','如果给 TA 做一道菜，你想做什么？',
  '你希望十年后的我们是什么样子？','TA 的哪个习惯让你又爱又恨？','你们之间最难忘的一次旅行？',
  '如果要给我们的故事起个名字，你会叫它什么？','你偷偷为 TA 做过但没说出口的事？',
  '你觉得我们最像哪对电影情侣？','TA 什么时候最有魅力？','你最想再去一次的我们去过的地方？',
  '如果明天放假，你最想和 TA 做什么？','你收藏了哪句想对 TA 说的话？','TA 送过你最喜欢的一件东西？',
  '你什么时候开始确定"就是 TA 了"？','我们的下一次旅行你想去哪里？','TA 的小动作里你最喜欢哪个？',
  '你为 TA 改变过的一个习惯？','你最想听 TA 再说一遍的一句话？','如果拍我们的小电影，片名是？',
  '你手机里最舍不得删的一张照片是哪张（和 TA 有关）？','你希望 TA 改掉的一个小毛病（善意版）？',
  '如果可以交换身份一天，你最想体验 TA 的什么？','你最近一次想 TA 是在什么时候？',
  '你最喜欢和 TA 一起的哪个日常瞬间？','写一句只有我们俩懂的情话？',
];
function dailyPayload(device) {
  const coupleId = device.coupleId;
  const today = D.todayStr();
  const q = QUESTIONS[Math.floor(Date.parse(today + 'T00:00:00') / D.DAY) % QUESTIONS.length];
  const answers = (db.dailyAnswers[coupleId] && db.dailyAnswers[coupleId][today]) || {};
  const partner = partnerOf(device);
  const mine = answers[device.id] || null;
  const partnerAns = partner ? answers[partner.id] || null : null;
  // 互相可见规则：我提交后才能看到 TA 的答案
  return { date: today, question: q, myAnswer: mine,
    partnerAnswer: mine ? partnerAns : (partnerAns ? 'hidden' : null), partnerAnswered: !!partnerAns };
}
app.post('/api/games/daily/answer', auth, (req, res) => {
  if (!req.device.coupleId) return res.status(400).json({ error: '尚未配对' });
  const text = req.body && typeof req.body.text === 'string' ? req.body.text.trim().slice(0, 200) : '';
  if (!text) return res.status(400).json({ error: '答案不能为空' });
  const today = D.todayStr();
  db.dailyAnswers[req.device.coupleId] = db.dailyAnswers[req.device.coupleId] || {};
  db.dailyAnswers[req.device.coupleId][today] = db.dailyAnswers[req.device.coupleId][today] || {};
  db.dailyAnswers[req.device.coupleId][today][req.device.id] = text;
  save();
  pushToCouple(req.device.coupleId, { type: 'daily_answer', ts: Date.now() }, req.device.id);
  res.json({ daily: dailyPayload(req.device) });
});

app.get('/api/ping', (req, res) => res.json({ ok: true, name: 'WeGood', time: Date.now() }));

app.get('/', (req, res) => res.type('html').send('<h1 style="font-family:sans-serif">WeGood 服务端运行中 ❤️</h1>'));

// ---------- SSE ----------
const sseConnections = new Map(); // deviceId -> Set<res>

app.get('/api/stream', (req, res) => {
  const device = db.devices[req.query.id];
  if (!device || device.secret !== req.query.token) return res.status(401).end();
  res.writeHead(200, {
    'Content-Type': 'text/event-stream', 'Cache-Control': 'no-cache',
    Connection: 'keep-alive', 'X-Accel-Buffering': 'no',
  });
  res.write(`data: ${JSON.stringify({ type: 'hello', ts: Date.now() })}\n\n`);
  if (!sseConnections.has(device.id)) sseConnections.set(device.id, new Set());
  const firstConn = sseConnections.get(device.id).size === 0;
  sseConnections.get(device.id).add(res);
  device.online = true;
  if (firstConn && device.coupleId) pushToCouple(device.coupleId, { type: 'presence', online: true, who: 'partner', ts: Date.now() }, device.id);

  const ping = setInterval(() => { try { res.write(': ping\n\n'); } catch { /* noop */ } }, 25000);
  req.on('close', () => {
    clearInterval(ping);
    const set = sseConnections.get(device.id);
    if (set) { set.delete(res); if (set.size === 0) {
      sseConnections.delete(device.id); device.online = false;
      if (device.coupleId) pushToCouple(device.coupleId, { type: 'presence', online: false, who: 'partner', ts: Date.now() }, device.id);
    } }
  });
});

// ---------- 纪念日提醒 tick ----------
setInterval(() => {
  const now = new Date();
  const anns = Object.values(db.anniversaries);
  const fired = new Set();
  for (const a of anns) {
    if (!D.shouldFireReminder(a, now)) continue;
    const key = D.reminderKey(a.id, now);
    if (db.remindersSent[key]) continue;
    db.remindersSent[key] = Date.now();
    const days = D.daysUntil(D.nextOccurrence(a.date, a.repeatYearly, now), now);
    const text = days === 0 ? `「${a.name}」就是今天！` : `还有 ${days} 天就是「${a.name}」啦`;
    pushToCouple(a.coupleId, { type: 'reminder', anniversaryId: a.id, name: a.name, days, text, ts: Date.now() });
    addEvent(a.coupleId, { kind: 'sys', text: `提醒：${text}` });
    fired.add(key);
  }
  // 清理 30 天前的去重记录
  const cutoff = Date.now() - 30 * D.DAY;
  for (const [k, ts] of Object.entries(db.remindersSent)) if (ts < cutoff) delete db.remindersSent[k];
  if (fired.size) save();
}, TICK_MS).unref();

function pub(d) { return { id: d.id, name: d.name, code: d.code, online: !!d.online, createdAt: d.createdAt }; }

process.on('exit', () => { try { store.saveNow(); } catch { /* noop */ } });
app.listen(PORT, () => console.log(`WeGood server on http://localhost:${PORT}  (TZ=${process.env.TZ || 'system'})`));
