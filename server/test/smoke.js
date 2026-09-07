// 端到端冒烟测试：模拟两台设备 + SSE 客户端，覆盖注册→配对→爱心→纪念日→井字棋→每日一问→解绑
const assert = require('assert');

const BASE = process.env.BASE_URL || 'http://localhost:3999';

function sseConnect(deviceId, secret) {
  // 简易 SSE 客户端（用 fetch 流式读取），返回 {events, close}
  const events = [];
  const controller = new AbortController();
  const done = fetch(`${BASE}/api/stream?id=${deviceId}&token=${secret}`, { signal: controller.signal })
    .then((res) => {
      assert.strictEqual(res.status, 200, 'SSE 应为 200');
      const reader = res.body.getReader();
      const decoder = new TextDecoder();
      let buf = '';
      (function pump() {
        return reader.read().then(({ done, value }) => {
          if (done) return;
          buf += decoder.decode(value, { stream: true });
          let i;
          while ((i = buf.indexOf('\n\n')) >= 0) {
            const chunk = buf.slice(0, i); buf = buf.slice(i + 2);
            for (const line of chunk.split('\n')) {
              if (line.startsWith('data: ')) events.push(JSON.parse(line.slice(6)));
            }
          }
          return pump();
        });
      })();
    });
  return { events, close: () => controller.abort(), done: done.catch(() => {}) };
}

const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

async function api(method, path, body, device) {
  const res = await fetch(BASE + path, {
    method,
    headers: {
      'Content-Type': 'application/json',
      ...(device ? { 'x-device-id': device.deviceId, 'x-device-secret': device.secret } : {}),
    },
    body: body ? JSON.stringify(body) : undefined,
  });
  const json = await res.json().catch(() => ({}));
  return { status: res.status, json };
}

async function waitFor(events, type, timeoutMs = 3000) {
  const start = Date.now();
  while (Date.now() - start < timeoutMs) {
    const found = events.find((e) => e.type === type);
    if (found) return found;
    await sleep(50);
  }
  throw new Error(`等待 SSE 事件 ${type} 超时`);
}

async function main() {
  const ping = await api('GET', '/api/ping');
  assert.strictEqual(ping.status, 200, '服务未启动');

  // 1. 注册两台设备
  const A = (await api('POST', '/api/register', { name: '小A' })).json;
  const B = (await api('POST', '/api/register', { name: '小B' })).json;
  assert.ok(A.deviceId && B.deviceId && A.code !== B.code, '注册应返回唯一凭证与配对码');

  // 2. 建立双方 SSE 监听
  const sseA = sseConnect(A.deviceId, A.secret);
  const sseB = sseConnect(B.deviceId, B.secret);
  await waitFor(sseA.events, 'hello');
  await waitFor(sseB.events, 'hello');

  // 3. 鉴权失败用例
  assert.strictEqual((await api('GET', '/api/me', null, { deviceId: 'bad', secret: 'bad' })).status, 401, '错误凭证应 401');

  // 4. 配对：A 用 B 的配对码
  assert.strictEqual((await api('POST', '/api/pair', { code: A.code }, A)).status, 400, '不能和自己配对');
  assert.strictEqual((await api('POST', '/api/pair', { code: 'ZZZZZZ' }, A)).status, 404, '配对码不存在应 404');
  assert.strictEqual((await api('POST', '/api/pair', { code: B.code }, A)).status, 200);
  const pairedA = await waitFor(sseA.events, 'paired');
  const pairedB = await waitFor(sseB.events, 'paired');
  assert.strictEqual(pairedA.partnerName, '小B');
  assert.strictEqual(pairedB.partnerName, '小A');

  // 5. 在线状态
  const meA = (await api('GET', '/api/me', null, A)).json;
  assert.ok(meA.partner && meA.partner.online === true, '对方应显示在线');
  assert.ok(meA.couple && meA.couple.createdAt > 0);

  // 6. 爱心：A 发，B 收
  assert.strictEqual((await api('POST', '/api/hearts', { kind: 'kiss' }, A)).status, 200);
  const heart = await waitFor(sseB.events, 'heart');
  assert.strictEqual(heart.kind, 'kiss');
  assert.strictEqual(heart.fromName, '小A');

  // 7. 纪念日：A 建（过去，每年重复，提前1天提醒），B 同步可见
  const created = await api('POST', '/api/anniversaries',
    { name: '在一起', date: '2024-09-07', repeatYearly: true, remindDaysBefore: 1, remindTime: '09:00' }, A);
  assert.strictEqual(created.status, 200);
  await waitFor(sseB.events, 'anniversary_changed');
  const annsB = (await api('GET', '/api/me', null, B)).json.anniversaries;
  assert.strictEqual(annsB.length, 1, 'B 应同步到纪念日');
  assert.strictEqual(annsB[0].name, '在一起');

  // 8. 提醒触发逻辑（纯函数）
  const D = require('../dates');
  const noon = new Date(2026, 8, 6, 9, 30); // 2026-09-06（提前一天）
  assert.strictEqual(D.triggerDate('2026-09-07', false, 1, noon), '2026-09-06');
  assert.ok(D.shouldFireReminder({ date: '2026-09-07', repeatYearly: false, remindDaysBefore: 1, remindTime: '09:00' }, noon));
  assert.ok(!D.shouldFireReminder({ date: '2026-09-07', repeatYearly: false, remindDaysBefore: 1, remindTime: '10:00' }, noon));
  assert.strictEqual(D.nextOccurrence('2020-02-29', true, new Date(2026, 1, 10)), '2026-02-29'); // 平年2月29跳过（显示为3月1日则不符，直接跳到次年判断）
  assert.strictEqual(D.dayCount('2026-09-07', new Date(2026, 8, 7)), 1);
  assert.strictEqual(D.daysUntil('2026-09-08', new Date(2026, 8, 7)), 1);

  // 9. 井字棋：X 先行（A），B 抢走会被拒
  assert.strictEqual((await api('POST', '/api/games/ttt/move', { index: 4 }, B)).status, 409, '不该 B 先走');
  await api('POST', '/api/games/ttt/move', { index: 0 }, A);
  await api('POST', '/api/games/ttt/move', { index: 3 }, B);
  await api('POST', '/api/games/ttt/move', { index: 1 }, A);
  await api('POST', '/api/games/ttt/move', { index: 4 }, B);
  const win = await api('POST', '/api/games/ttt/move', { index: 2 }, A);
  assert.strictEqual(win.json.ttt.winner, 'X', 'A(X) 应获胜');
  assert.deepStrictEqual(win.json.ttt.winLine, [0, 1, 2]);
  await waitFor(sseB.events, 'ttt');

  // 10. 每日一问：双方都提交后互相可见
  const ansA = await api('POST', '/api/games/daily/answer', { text: '先说我的' }, A);
  assert.strictEqual(ansA.json.daily.partnerAnswered, false);
  await api('POST', '/api/games/daily/answer', { text: '我也来答' }, B);
  const meAfter = (await api('GET', '/api/me', null, A)).json.daily;
  assert.strictEqual(meAfter.partnerAnswer, '我也来答', 'A 提交后应看到 B 的答案');

  // 11. 解绑：数据清空，双方收到事件
  assert.strictEqual((await api('POST', '/api/unpair', null, A)).status, 200);
  await waitFor(sseA.events, 'unpaired');
  await waitFor(sseB.events, 'unpaired');
  const meA2 = (await api('GET', '/api/me', null, A)).json;
  assert.ok(meA2.partner === null && meA2.couple === null && meA2.anniversaries.length === 0, '解绑后应清空');

  sseA.close(); sseB.close();
  console.log('✅ 全部冒烟测试通过（注册/配对/爱心/纪念日/提醒/井字棋/每日一问/解绑）');
  process.exit(0);
}

main().catch((e) => { console.error('❌ 测试失败:', e.message); process.exit(1); });
