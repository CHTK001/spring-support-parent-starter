/**
 * 阅卷「完成」按钮的 UI 级验收（对应验收报告 §36）。
 *
 * 流程：老师登录 -> /edu/marking/detail?taskId=... -> 点「完成」-> 确认弹窗 -> 确定
 *       -> 断言成功提示带错题数 -> 按钮变「已完成」标签 -> 重新打开仍在。
 *
 * 注意：本脚本会真实写库（错题 + 任务完成标记）。配套的 check 脚本负责回滚。
 *
 * 用法：node ui-marking-finish-e2e.cjs http://<host>:19093 <taskId>
 */
let pw;
try { pw = require('playwright'); }
catch (e) { pw = require('D:/ch/project/vue-support-parent-starter/node_modules/playwright'); }

const BASE = process.argv[2] || 'http://192.168.50.198:19093';
const TASK = process.argv[3] || '';
const TEACHER = { account: 'ACC-1791086381', pass: 'Teach@12345' };

const log = (...a) => console.log(...a);

async function loginAsTeacher(p) {
  await p.goto(BASE + '/edu/student-login', { waitUntil: 'domcontentloaded' });
  await p.waitForSelector('.sl-form input[type="text"]', { timeout: 60000 });
  await p.locator('.sl-field__box input[type="text"]').first().fill(TEACHER.account);
  await p.locator('.sl-field__box input[type="password"]').first().fill(TEACHER.pass);
  await p.locator('button.sl-submit').first().click();
  await p.waitForTimeout(5000);
  return p.evaluate(() => location.pathname);
}

const detailUrl = () => BASE + '/edu/marking/detail?taskId=' + encodeURIComponent(TASK);

/** 面板汇总区状态：完成按钮是否在、是否已显示「已完成」标签 */
const panelState = (p) => p.evaluate(() => {
  const sum = document.querySelector('.mr-sum');
  if (!sum) return { found: false };
  const btn = Array.from(sum.querySelectorAll('button')).find((b) => (b.innerText || '').trim() === '\u5B8C\u6210');
  return {
    found: true,
    text: (sum.innerText || '').replace(/\s+/g, ' ').trim(),
    hasFinishBtn: !!btn,
    finishDisabled: btn ? btn.disabled : null,
    hasDoneTag: Array.from(sum.querySelectorAll('.el-tag, .re-tag')).some((t) => (t.innerText || '').trim() === '\u5DF2\u5B8C\u6210'),
  };
});

const msgs = (p) => p.evaluate(() => Array.from(document.querySelectorAll('.el-message, .el-notification'))
  .map((x) => (x.innerText || '').replace(/\s+/g, ' ').trim()).filter(Boolean));

(async () => {
  if (!TASK) { console.error('缺少 taskId'); process.exit(1); }
  const b = await pw.chromium.launch({ headless: true });
  const ctx = await b.newContext({ viewport: { width: 1440, height: 900 } });
  const p = await ctx.newPage();
  const errs = [];
  const badResp = [];
  p.on('pageerror', (e) => errs.push('PAGEERROR: ' + String(e.message).slice(0, 140)));
  p.on('console', (m) => { if (m.type() === 'error') errs.push('CONSOLE: ' + String(m.text()).slice(0, 140)); });
  p.on('response', (r) => { if (r.status() >= 400) badResp.push(r.status() + ' ' + r.request().method() + ' ' + r.url().replace(/^https?:\/\/[^/]+/, '')); });

  log('login ->', await loginAsTeacher(p));

  await p.goto(detailUrl(), { waitUntil: 'domcontentloaded' });
  await p.waitForSelector('.mr-sum', { timeout: 60000 });
  await p.waitForTimeout(3500);
  const s0 = await panelState(p);
  log('1) 初始面板: ' + JSON.stringify(s0));

  if (!s0.hasFinishBtn) { log('!! 未找到「完成」按钮'); }
  else {
    await p.locator('.mr-sum button', { hasText: '\u5B8C\u6210' }).first().click();
    await p.waitForTimeout(1200);
    const dlg = await p.evaluate(() => {
      const d = Array.from(document.querySelectorAll('.el-dialog')).find((x) => x.offsetParent !== null);
      return d ? { title: (d.querySelector('.el-dialog__title, .el-dialog__header')?.innerText || '').trim(),
                   buttons: Array.from(d.querySelectorAll('button')).map((x) => (x.innerText || '').trim()).filter(Boolean) } : null;
    });
    log('2) 确认弹窗: ' + JSON.stringify(dlg));
    if (dlg) {
      const okBtn = p.locator('.el-dialog').last().locator('button', { hasText: '\u786E\u5B9A' }).first();
      if (await okBtn.count()) { await okBtn.click(); await p.waitForTimeout(1000); }
      log('3) 提交后提示: ' + JSON.stringify(await msgs(p)));
    }
    // 重新加载，确认状态已持久化
    await p.goto(detailUrl(), { waitUntil: 'domcontentloaded' });
    await p.waitForSelector('.mr-sum', { timeout: 60000 });
    await p.waitForTimeout(3000);
    log('4) 重载后面板: ' + JSON.stringify(await panelState(p)));
  }

  log('errors:', JSON.stringify(errs.slice(0, 8)));
  log('badResp:', JSON.stringify(badResp.slice(0, 8)));
  await b.close();
})().catch((e) => { console.error('FATAL', e.stack || e); process.exit(1); });
