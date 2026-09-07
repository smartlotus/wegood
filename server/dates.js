// 日期相关纯函数，便于单独测试。所有日期均为 'YYYY-MM-DD' 本地日期字符串。

const DAY = 86400000;

function todayStr(now = new Date()) {
  return toDateStr(now);
}

function toDateStr(d) {
  const y = d.getFullYear();
  const m = String(d.getMonth() + 1).padStart(2, '0');
  const day = String(d.getDate()).padStart(2, '0');
  return `${y}-${m}-${day}`;
}

// 'YYYY-MM-DD' -> 当日零点的时间戳（按本地时区）
function parseDate(s) {
  const [y, m, d] = s.split('-').map(Number);
  return new Date(y, m - 1, d).getTime();
}

// 过去日期距今天数（今天算第 0 天：date=昨天 → 1，date=今天 → 0）
function daysSince(dateStr, now = new Date()) {
  return Math.floor((parseDate(toDateStr(now)) - parseDate(dateStr)) / DAY);
}

// 未来日期剩余天数（date=明天 → 1，date=今天 → 0）
function daysUntil(dateStr, now = new Date()) {
  return Math.floor((parseDate(dateStr) - parseDate(toDateStr(now))) / DAY);
}

// "在一起第 N 天"：当天即第 1 天
function dayCount(dateStr, now = new Date()) {
  return daysSince(dateStr, now) + 1;
}

// 每年重复时：该纪念日今年（或已过则明年）的日期；不重复则原样返回
function nextOccurrence(dateStr, repeatYearly, now = new Date()) {
  if (!repeatYearly) return dateStr;
  const [, m, d] = dateStr.split('-');
  const y = now.getFullYear();
  const thisYear = `${y}-${m}-${d}`;
  if (parseDate(thisYear) >= parseDate(toDateStr(now))) return thisYear;
  return `${y + 1}-${m}-${d}`;
}

// 提醒触发日：目标日期提前 remindDaysBefore 天
function triggerDate(dateStr, repeatYearly, remindDaysBefore, now = new Date()) {
  const occ = nextOccurrence(dateStr, repeatYearly, now);
  return toDateStr(new Date(parseDate(occ) - remindDaysBefore * DAY));
}

function nowHHmm(now = new Date()) {
  return `${String(now.getHours()).padStart(2, '0')}:${String(now.getMinutes()).padStart(2, '0')}`;
}

// 是否应当在该时刻触发提醒（每天最多一次，由 remindersSent 去重）
function shouldFireReminder({ date, repeatYearly, remindDaysBefore, remindTime }, now = new Date()) {
  if (remindDaysBefore == null || !remindTime) return false;
  if (triggerDate(date, repeatYearly, remindDaysBefore, now) !== toDateStr(now)) return false;
  return nowHHmm(now) >= remindTime;
}

// 去重键：同一纪念日 + 同一次触发日 只发一次
function reminderKey(annId, now = new Date()) {
  return `${annId}|${toDateStr(now)}`;
}

module.exports = {
  DAY, toDateStr, todayStr, parseDate,
  daysSince, daysUntil, dayCount,
  nextOccurrence, triggerDate, nowHHmm,
  shouldFireReminder, reminderKey,
};
