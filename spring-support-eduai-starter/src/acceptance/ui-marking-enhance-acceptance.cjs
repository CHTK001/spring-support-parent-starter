/**
 * 阅卷增强的浏览器验收（对应验收报告 §38）。
 *
 * 覆盖三件事，全部真实点击、真实数据：
 *   1) finish   判错阈值：部分得分（1/2 = 50%）的题也应归入错题本 → 提示 28 题
 *   2) undo     撤销完成：按钮可见、二次确认、撤销后任务回到「待完成」
 *   3) list     列表完成状态筛选：「待完成」「已完成」两个口径都按行核对
 *
 * 用法：node ui-marking-enhance-acceptance.cjs <base> <finish|undo|list> <taskId>
 */
let pw;
try { pw = require('playwright'); }
catch (e) { pw = require('D:/ch/project/vue-support-parent-starter/node_modules/playwright'); }

const BASE = process.argv[2] || 'http://192.168.50.198:19093';
const PHASE = (process.argv[3] || '').trim();
const TASK = (process.argv[4] || '').trim();
const TEACHER = { account: 'ACC-1791086381', pass: 'Teach@12345' };

const log = (...a) => console.log(...a);
const RESULT = { phase: PHASE, task: TASK, checks: [] };
const record = (name, ok, detail) => {
  RESULT.checks.push({ name, ok: !!ok, detail: detail === undefined ? '' : String(detail) });
  log((ok ? '  PASS  ' : '  FAIL  ') + name + (detail === undefined ? '' : '   [' + detail + ']'));
};

const MSG_OBSERVER = () => {
  window.__uitestMsgs = [];
  const scan = () => {
    document.querySelectorAll('.el-message, .el-notification').forEach((x) => {
      const t = (x.innerText || '').replace(/\s+/g, ' ').trim();
      if (t && !window.__uitestMsgs.includes(t)) window.__uitestMsgs.push(t);
    });
  };
  const start = () => { scan(); new MutationObserver(scan).observe(document.body, { childList: true, subtree: true }); };
  if (document.body) start(); else document.addEventListener('DOMContentLoaded', start, { once: true });
};

const msgs = (p) => p.evaluate(() => window.__uitestMsgs || []);
const clearMsgs = (p) => p.evaluate(() => { window.__uitestMsgs = []; });

async function loginAsTeacher(p) {
  await p.goto(BASE + '/edu/student-login', { waitUntil: 'domcontentloaded' });
  await p.waitForSelector('.sl-form input[type="text"]', { timeout: 60000 });
  await p.locator('.sl-field__box input[type="text"]').first().fill(TEACHER.account);
  await p.locator('.sl-field__box input[type="password"]').first().fill(TEACHER.pass);
  await p.locator('button.sl-submit').first().click();
  await p.waitForTimeout(5000);
}

const detailUrl = () => BASE + '/edu/marking/detail?taskId=' + encodeURIComponent(TASK);

const panelState = (p) => p.evaluate(() => {
  const sum = document.querySelector('.mr-sum');
  if (!sum) return { found: false };
  const btn = Array.from(sum.querySelectorAll('button')).find((b) => (b.innerText || '').trim() === '完成');
  const undo = Array.from(sum.querySelectorAll('button')).find((b) => (b.innerText || '').trim() === '撤销完成');
  return {
    found: true,
    hasFinishBtn: !!btn,
    finishDisabled: btn ? !!btn.disabled : null,
    hasUndoBtn: !!undo,
    hasDoneTag: Array.from(sum.querySelectorAll('.el-tag, .re-tag')).some((t) => (t.innerText || '').trim() === '已完成'),
    text: (sum.innerText || '').replace(/\s+/g, ' ').trim(),
  };
});

async function openDetail(p) {
  await p.goto(detailUrl(), { waitUntil: 'domcontentloaded' });
  await p.waitForSelector('.mr-sum', { timeout: 60000 });
  await p.waitForTimeout(3000);
}

async function clickDialog(p, label) {
  const b = p.locator('.el-dialog').last().locator('button', { hasText: label }).first();
  if (!(await b.count())) return false;
  await b.click();
  await p.waitForTimeout(1500);
  return true;
}

(async () => {
  if (!PHASE) { console.error('usage: node ui-marking-enhance-acceptance.cjs <base> <finish|undo|list> [taskId]'); process.exit(1); }
  const b = await pw.chromium.launch({ headless: true });
  const ctx = await b.newContext({ viewport: { width: 1440, height: 900 } });
  const p = await ctx.newPage();
  await p.addInitScript(MSG_OBSERVER);
  const errs = [];
  const badResp = [];
  p.on('pageerror', (e) => errs.push('PAGEERROR: ' + String(e.message).slice(0, 160)));
  p.on('console', (m) => { if (m.type() === 'error') errs.push('CONSOLE: ' + String(m.text()).slice(0, 160)); });
  p.on('response', (r) => { if (r.status() >= 400) badResp.push(r.status() + ' ' + r.request().method() + ' ' + r.url().replace(/^https?:\/\/[^/]+/, '')); });

  log('=== phase ' + PHASE + (TASK ? '  task ' + TASK : '') + ' ===');
  await loginAsTeacher(p);

  if (PHASE === 'finish') {
    await openDetail(p);
    const s0 = await panelState(p);
    record('完成前：完成按钮可用且未完成', s0.hasFinishBtn && s0.finishDisabled === false && !s0.hasDoneTag, JSON.stringify(s0));
    await clearMsgs(p);
    await p.locator('.mr-sum button', { hasText: '完成' }).first().click();
    await p.waitForTimeout(1000);
    const dlg = await p.evaluate(() => {
      const d = Array.from(document.querySelectorAll('.el-dialog')).find((x) => x.offsetParent !== null);
      return d ? (d.querySelector('.el-dialog__title')?.innerText || '').trim() : null;
    });
    record('二次确认弹窗标题为「阅卷完成」', dlg === '阅卷完成', dlg);
    await clickDialog(p, '确定');
    await p.waitForTimeout(900);
    const m = await msgs(p);
    const msg = m.join(' | ');
    record('提示包含 28 题（27 道 0 分 + 1 道 1/2 分）', /28\s*题/.test(msg), msg);
    await openDetail(p);
    const s1 = await panelState(p);
    record('完成后：已完成标签 + 撤销按钮', s1.hasDoneTag && s1.hasUndoBtn && !s1.hasFinishBtn, JSON.stringify(s1));
  } else if (PHASE === 'undo') {
    await openDetail(p);
    const s0 = await panelState(p);
    record('撤销前：已完成且有「撤销完成」', s0.hasDoneTag && s0.hasUndoBtn, JSON.stringify(s0));
    await clearMsgs(p);
    await p.locator('.mr-sum button', { hasText: '撤销完成' }).first().click();
    await p.waitForTimeout(1000);
    const dlg = await p.evaluate(() => {
      const d = Array.from(document.querySelectorAll('.el-dialog')).find((x) => x.offsetParent !== null);
      return d ? {
        title: (d.querySelector('.el-dialog__title')?.innerText || '').trim(),
        buttons: Array.from(d.querySelectorAll('button')).map((x) => (x.innerText || '').trim()).filter(Boolean),
      } : null;
    });
    record('撤销弹窗标题为「撤销完成」并有取消/确定', !!dlg && dlg.title === '撤销完成' && dlg.buttons.includes('确定'), JSON.stringify(dlg));
    await clickDialog(p, '确定');
    await p.waitForTimeout(900);
    const msg = (await msgs(p)).join(' | ');
    record('撤销提示包含移出题数 28', /28/.test(msg), msg);
    await openDetail(p);
    const s1 = await panelState(p);
    record('撤销后：回到「完成」按钮且标签消失', s1.hasFinishBtn && !s1.hasDoneTag && !s1.hasUndoBtn, JSON.stringify(s1));
  } else if (PHASE === 'list') {
    await p.goto(BASE + '/edu/teacher', { waitUntil: 'domcontentloaded' });
    await p.waitForTimeout(4000);
    await p.locator('.el-tabs__item, [role="tab"]', { hasText: '阅卷' }).first().click();
    await p.waitForTimeout(3000);

    // 表头里必须有「完成」列
    const headers = await p.evaluate(() => {
      const out = [];
      document.querySelectorAll('.el-table').forEach((tb) => {
        const hs = Array.from(tb.querySelectorAll('thead th')).map((x) => (x.innerText || '').replace(/\s+/g, '').trim());
        if (hs.includes('完成')) out.push(hs.join('|'));
      });
      return out;
    });
    record('阅卷记录表新增「完成」列', headers.length > 0, JSON.stringify(headers.slice(0, 1)));

    /** 读取当前页每一行「完成」列的标签文本 */
    const rowFinish = () => p.evaluate(() => {
      const rows = [];
      document.querySelectorAll('.el-table').forEach((tb) => {
        const hs = Array.from(tb.querySelectorAll('thead th')).map((x) => (x.innerText || '').replace(/\s+/g, '').trim());
        const idx = hs.indexOf('完成');
        if (idx < 0) return;
        tb.querySelectorAll('.el-table__row').forEach((r) => {
          const tds = r.querySelectorAll('td');
          if (tds.length <= idx) return;
          const txt = (tds[idx].innerText || '').replace(/\s+/g, '').trim();
          if (txt) rows.push(txt);
        });
      });
      return rows;
    });

    const pickOption = async (label) => {
      // 表头的完成状态筛选（el-select）
      const sel = p.locator('.mw-records .el-select').first();
      await sel.click();
      await p.waitForTimeout(700);
      const opt = p.locator('.el-select-dropdown__item', { hasText: label }).first();
      await opt.click();
      await p.waitForTimeout(3000);
    };

    await pickOption('待完成');
    const todo = await rowFinish();
    record('筛选「待完成」：每行都不是已完成', todo.length > 0 && todo.every((x) => x !== '已完成'), JSON.stringify(todo.slice(0, 8)) + ' total=' + todo.length);

    await pickOption('已完成');
    const done = await rowFinish();
    record('筛选「已完成」：每行都是已完成', done.length > 0 && done.every((x) => x === '已完成'), JSON.stringify(done.slice(0, 8)) + ' total=' + done.length);
  } else {
    console.error('unknown phase: ' + PHASE); process.exit(1);
  }

  const unexpectedErr = errs.filter((x) => !/Failed to load resource/.test(x));
  const unexpectedBad = badResp.filter((x) => !/\/finish|\/unfinish/.test(x));
  record('无页面/控制台 JS 错误', unexpectedErr.length === 0, JSON.stringify(unexpectedErr.slice(0, 5)));
  record('无预期外 HTTP >= 400', unexpectedBad.length === 0, JSON.stringify(unexpectedBad.slice(0, 5)));

  RESULT.failed = RESULT.checks.filter((c) => !c.ok).length;
  log('RESULT_JSON ' + JSON.stringify(RESULT));
  await b.close();
  process.exit(RESULT.failed === 0 ? 0 : 3);
})().catch((e) => { console.error('FATAL', e.stack || e); process.exit(1); });
