/**
 * 阅卷「完成」——分阶段浏览器验收（对应验收报告 §37）。
 *
 * 生产级口径：真实登录、真实点击、真实二次确认、真实数据核对；每条断言都要在
 * 浏览器里点出来，并在库里核对（由外部脚本在阶段之间做 DB 断言）。
 *
 * 分三个阶段（由第 3 个参数选择），便于在阶段之间查库：
 *   cancel     点「完成」-> 弹窗 -> 点「取消」          期望：无任何写入
 *   anonymous  匿名卷点「完成」-> 确定                  期望：拒绝并提示，无写入
 *   finish     正常卷点「完成」-> 确定                  期望：写入 N 条 + 标记已完成 + 标签出现
 *
 * 用法：
 *   node ui-marking-finish-acceptance.cjs <base> <phase> <taskId>
 */
let pw;
try { pw = require('playwright'); }
catch (e) { pw = require('D:/ch/project/vue-support-parent-starter/node_modules/playwright'); }

const BASE = process.argv[2] || 'http://192.168.50.198:19093';
const PHASE = (process.argv[3] || '').trim();
const TASK = (process.argv[4] || '').trim();
const TEACHER = { account: 'ACC-1791086381', pass: 'Teach@12345' };

const log = (...a) => console.log(...a);

async function loginAsTeacher(p) {
  await p.goto(BASE + '/edu/student-login', { waitUntil: 'domcontentloaded' });
  await p.waitForSelector('.sl-form input[type="text"]', { timeout: 60000 });
  await p.locator('.sl-field__box input[type="text"]').first().fill(TEACHER.account);
  await p.locator('.sl-field__box input[type="password"]').first().fill(TEACHER.pass);
  await p.locator('button.sl-submit').first().click();
  await p.waitForTimeout(5000);
}

const detailUrl = (taskId) => BASE + '/edu/marking/detail?taskId=' + encodeURIComponent(taskId);

const panelState = (p) => p.evaluate(() => {
  const sum = document.querySelector('.mr-sum');
  if (!sum) return { found: false };
  const btn = Array.from(sum.querySelectorAll('button')).find((b) => (b.innerText || '').trim() === '\u5B8C\u6210');
  return {
    found: true,
    hasFinishBtn: !!btn,
    finishDisabled: btn ? !!btn.disabled : null,
    hasDoneTag: Array.from(sum.querySelectorAll('.el-tag, .re-tag')).some((t) => (t.innerText || '').trim() === '\u5DF2\u5B8C\u6210'),
  };
});

const dialogState = (p) => p.evaluate(() => {
  const d = Array.from(document.querySelectorAll('.el-dialog')).find((x) => x.offsetParent !== null);
  if (!d) return null;
  return {
    title: (d.querySelector('.el-dialog__title, .el-dialog__header')?.innerText || '').replace(/\s+/g, ' ').trim(),
    buttons: Array.from(d.querySelectorAll('button')).map((x) => (x.innerText || '').replace(/\s+/g, ' ').trim()).filter(Boolean),
  };
});

/**
 * 提示文案用 MutationObserver 全程留证。
 *
 * <p>直接查 DOM 会漏：Element Plus 的 message 默认 3 秒自动消失，断言一旦慢一点
 * 就取到空数组，容易被误判成「后端没提示」。这里在页面脚本阶段就开始记录。</p>
 */
const MSG_OBSERVER = () => {
  window.__uitestMsgs = [];
  const scan = () => {
    document.querySelectorAll('.el-message, .el-notification').forEach((x) => {
      const t = (x.innerText || '').replace(/\s+/g, ' ').trim();
      if (t && !window.__uitestMsgs.includes(t)) window.__uitestMsgs.push(t);
    });
  };
  const start = () => {
    scan();
    new MutationObserver(scan).observe(document.body, { childList: true, subtree: true });
  };
  if (document.body) start(); else document.addEventListener('DOMContentLoaded', start, { once: true });
};

const msgs = (p) => p.evaluate(() => window.__uitestMsgs || []);
const clearMsgs = (p) => p.evaluate(() => { window.__uitestMsgs = []; });

async function openDetail(p, taskId) {
  await p.goto(detailUrl(taskId), { waitUntil: 'domcontentloaded' });
  await p.waitForSelector('.mr-sum', { timeout: 60000 });
  await p.waitForTimeout(3000);
}

async function clickFinish(p) {
  const btn = p.locator('.mr-sum button', { hasText: '\u5B8C\u6210' }).first();
  if (!(await btn.count())) return false;
  await btn.click();
  await p.waitForTimeout(1200);
  return true;
}

async function clickDialogButton(p, label) {
  const b = p.locator('.el-dialog').last().locator('button', { hasText: label }).first();
  if (!(await b.count())) return false;
  await b.click();
  await p.waitForTimeout(1200);
  return true;
}

const RESULT = { phase: PHASE, task: TASK, checks: [] };
const record = (name, ok, detail) => {
  RESULT.checks.push({ name, ok: !!ok, detail: detail === undefined ? '' : String(detail) });
  log((ok ? '  PASS  ' : '  FAIL  ') + name + (detail === undefined ? '' : '   [' + detail + ']'));
};

(async () => {
  if (!PHASE || !TASK) { console.error('usage: node ui-marking-finish-acceptance.cjs <base> <cancel|anonymous|finish> <taskId>'); process.exit(1); }
  const b = await pw.chromium.launch({ headless: true });
  const ctx = await b.newContext({ viewport: { width: 1440, height: 900 } });
  const p = await ctx.newPage();
  await p.addInitScript(MSG_OBSERVER);
  const errs = [];
  const badResp = [];
  p.on('pageerror', (e) => errs.push('PAGEERROR: ' + String(e.message).slice(0, 140)));
  p.on('console', (m) => { if (m.type() === 'error') errs.push('CONSOLE: ' + String(m.text()).slice(0, 140)); });
  p.on('response', (r) => { if (r.status() >= 400) badResp.push(r.status() + ' ' + r.request().method() + ' ' + r.url().replace(/^https?:\/\/[^/]+/, '')); });

  log('=== phase ' + PHASE + '  task ' + TASK + ' ===');
  await loginAsTeacher(p);
  await openDetail(p, TASK);

  if (PHASE === 'cancel') {
    const s0 = await panelState(p);
    record('完成按钮可见且可用', s0.hasFinishBtn && s0.finishDisabled === false, JSON.stringify(s0));
    const clicked = await clickFinish(p);
    record('点「完成」', clicked);
    const d = await dialogState(p);
    record('出现二次确认弹窗「阅卷完成」', !!d && d.title.includes('\u9605\u5377\u5B8C\u6210'), JSON.stringify(d));
    record('弹窗含 取消/确定', !!d && d.buttons.includes('\u53D6\u6D88') && d.buttons.includes('\u786E\u5B9A'), d ? d.buttons.join(',') : '');
    await clickDialogButton(p, '\u53D6\u6D88');
    const d2 = await dialogState(p);
    record('点「取消」后弹窗关闭', !d2, JSON.stringify(d2));
    await p.waitForTimeout(600);
    const s1 = await panelState(p);
    record('取消后仍是「完成」按钮（未完成）', s1.hasFinishBtn && !s1.hasDoneTag, JSON.stringify(s1));
    RESULT.noWriteExpected = true;
  } else if (PHASE === 'anonymous') {
    const s0 = await panelState(p);
    record('匿名卷：完成按钮可见', s0.hasFinishBtn, JSON.stringify(s0));
    const clicked = await clickFinish(p);
    record('匿名卷：点「完成」', clicked);
    const d = await dialogState(p);
    record('匿名卷：出现确认弹窗', !!d, JSON.stringify(d));
    await clearMsgs(p);
    await clickDialogButton(p, '\u786E\u5B9A');
    await p.waitForTimeout(1500);
    const m = await msgs(p);
    record('匿名卷：提示先设置学生', m.some((x) => x.includes('\u4FEE\u6539\u5B66\u751F') || x.includes('\u5B66\u53F7')), JSON.stringify(m));
    const s1 = await panelState(p);
    record('匿名卷：仍是「完成」按钮（未被标记完成）', s1.hasFinishBtn && !s1.hasDoneTag, JSON.stringify(s1));
    RESULT.noWriteExpected = true;
  } else if (PHASE === 'finish') {
    const s0 = await panelState(p);
    record('完成按钮可见且可用', s0.hasFinishBtn && s0.finishDisabled === false, JSON.stringify(s0));
    await clickFinish(p);
    const d = await dialogState(p);
    record('出现确认弹窗', !!d, JSON.stringify(d));
    await clearMsgs(p);
    await clickDialogButton(p, '\u786E\u5B9A');
    await p.waitForTimeout(800);
    const m = await msgs(p);
    record('成功提示含错题数', m.some((x) => x.includes('\u5DF2\u5F52\u5165\u5B66\u751F\u9519\u9898\u5E93')), JSON.stringify(m));
    RESULT.successMsg = m.join(' | ');
    await openDetail(p, TASK);
    const s1 = await panelState(p);
    record('重载后：按钮消失且显示「已完成」标签', !s1.hasFinishBtn && s1.hasDoneTag, JSON.stringify(s1));
    RESULT.wroteExpected = true;
  } else {
    console.error('unknown phase: ' + PHASE); process.exit(1);
  }

  // 匿名卷被拒绝时，后端按框架口径返回 HTTP 400（BusinessException 统一 400），
  // 浏览器会记一条 "Failed to load resource: 400"。这是**预期内**的，不算缺陷；
  // 其余任何 4xx/5xx 都必须为 0。
  const expected400 = (x) => PHASE === 'anonymous' && /\/finish/.test(x);
  const unexpectedBad = badResp.filter((x) => !expected400(x));
  const unexpectedErr = errs.filter((x) => !(PHASE === 'anonymous' && /status of 400/.test(x)));
  record('无预期外页面/控制台错误', unexpectedErr.length === 0, JSON.stringify(unexpectedErr.slice(0, 5)));
  record('无预期外 HTTP >= 400', unexpectedBad.length === 0, JSON.stringify(unexpectedBad.slice(0, 5)));
  if (PHASE === 'anonymous') {
    record('匿名卷返回了 HTTP 400（框架业务异常口径）', badResp.some(expected400), JSON.stringify(badResp.slice(0, 3)));
  }

  RESULT.failed = RESULT.checks.filter((c) => !c.ok).length;
  log('RESULT_JSON ' + JSON.stringify(RESULT));
  await b.close();
  process.exit(RESULT.failed === 0 ? 0 : 3);
})().catch((e) => { console.error('FATAL', e.stack || e); process.exit(1); });
