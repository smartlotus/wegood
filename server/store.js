// JSON 文件存储：两人规模的应用，单文件 + 原子写足够可靠
const fs = require('fs');
const path = require('path');

const DATA_DIR = process.env.DATA_DIR || path.join(__dirname, 'data');
const DB_FILE = path.join(DATA_DIR, 'db.json');

const DEFAULT_DB = () => ({
  devices: {},        // id -> {id, secret, name, code, coupleId, pushSubscription, createdAt}
  couples: {},        // id -> {id, members:[deviceId,deviceId], createdAt}
  anniversaries: {},  // id -> {...}
  events: [],         // 动态流，cap 50
  tttGames: {},       // coupleId -> {board, turn, winner, players}
  dailyAnswers: {},   // coupleId -> { 'YYYY-MM-DD': { deviceId: text } }
  remindersSent: {},  // key -> timestamp（提醒去重）
  vapid: null,        // {publicKey, privateKey}
});

let db = DEFAULT_DB();

function load() {
  fs.mkdirSync(DATA_DIR, { recursive: true });
  if (fs.existsSync(DB_FILE)) {
    try {
      db = Object.assign(DEFAULT_DB(), JSON.parse(fs.readFileSync(DB_FILE, 'utf8')));
    } catch (e) {
      console.error('[store] 读取 db.json 失败，使用空库:', e.message);
      db = DEFAULT_DB();
    }
  }
  return db;
}

let saveTimer = null;
function save() {
  // 合并短时间内的多次写入，但立即落盘语义对个人应用足够：同步写 + tmp/rename 原子替换
  if (saveTimer) return;
  saveTimer = setTimeout(() => {
    saveTimer = null;
    try {
      const tmp = DB_FILE + '.tmp';
      fs.writeFileSync(tmp, JSON.stringify(db, null, 2));
      fs.renameSync(tmp, DB_FILE);
    } catch (e) {
      console.error('[store] 写入失败:', e.message);
    }
  }, 50);
}
function saveNow() {
  if (saveTimer) { clearTimeout(saveTimer); saveTimer = null; }
  const tmp = DB_FILE + '.tmp';
  fs.writeFileSync(tmp, JSON.stringify(db, null, 2));
  fs.renameSync(tmp, DB_FILE);
}

module.exports = {
  get db() { return db; },
  load, save, saveNow,
  DATA_DIR,
};
