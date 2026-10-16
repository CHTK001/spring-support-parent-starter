/**
 * 受控补测：爬虫按规则跳过的「会改数据」控件（对应验收报告 §30）。
 *
 * 目标：全站点击爬虫（ui-full-click-acceptance.ps1）跳过 7 个破坏性控件，
 * 本脚本用「只走校验 / 只走取消」的受控路径把它们逐一点到，且不改变业务数据。
 *
 * 覆盖：
 *   A) /edu/question  批量导入弹窗  -> 取消（关闭）          + 空输入点「导入」得校验
 *   B) /edu/students  教师行「重置登录口令」 -> 取消（关闭）
 *   C) /edu/students  教师行「编辑」         -> 取消（关闭）
 *   D) /home          顶栏「退出登录」       -> 离开受保护区 + 清令牌（测完重新登回）
 *
 * 前置：目标环境已部署并有 admin（框架 /login）与教师/学生账号。
 * 用法：node ui-skipped-controls-e2e.cjs http://<host>:19093
 *       node ui-skipped-controls-e2e.cjs http://<host>:19093 b   # 只跑 B/C
 *
 * 依赖：playwright（本仓 vue-support-parent-starter/node_modules 内已装）。
 * 说明：登录页含全屏 loading 遮罩淡出期，真实鼠标操作前需等它让出命中，
 *       否则点击会被遮罩吞掉 —— 属脚本时序，不是产品缺陷。
 */
let pw;
try { pw = require('playwright'); }
catch (e) { pw = require('D:/ch/project/vue-support-parent-starter/node_modules/playwright'); }

const BASE = process.argv[2] || 'http://192.168.50.198:19093';
const ONLY = (process.argv[3] || '').toLowerCase(); // a|b|c|d 单跑，空=全跑

const ADMIN = { user: 'admin', pass: 'admin123' };
const TEACHER = { account: 'ACC-1791086381', pass: 'Teach@12345' };

const log = (...a) => console.log(...a);

/** 框架 /login 登录（需先等全屏遮罩让出滑块命中，再拖滑块）。 */
async function loginAsAdmin(p) {
  await p.goto(BASE + '/login', { waitUntil: 'domcontentloaded' });
  await p.waitForSelector('input[type="password"]', { timeout: 60000 });
  await p.waitForFunction(() => {
    const s = document.querySelector('.slider-verify');
    const t = s && s.querySelector('.slider-verify-thumb');
    if (!t) return false;
    const r = t.getBoundingClientRect();
    const el = document.elementFromPoint(r.x + r.width / 2, r.y + r.height / 2);
    return !!el && s.contains(el) && r.width > 0;
  }, null, { timeout: 40000, polling: 100 });
  await p.getByPlaceholder('\u8BF7\u8F93\u5165\u7528\u6237\u540D').fill(ADMIN.user);
  await p.getByPlaceholder('\u8BF7\u8F93\u5165\u5BC6\u7801').fill(ADMIN.pass);
  const wrap = p.locator('.slider-verify').first();
  const tb = await wrap.locator('.slider-verify-thumb').boundingBox();
  const wb = await wrap.boundingBox();
  const y = wb.y + wb.height / 2;
  const x0 = tb.x + tb.width / 2;
  const endX = wb.x + wb.width - tb.width / 2 - 1;
  await p.mouse.move(x0, y);
  await p.mouse.down();
  for (let i = 1; i <= 22; i++) {
    await p.mouse.move(x0 + (endX - x0) * i / 22, y);
    await p.waitForTimeout(18);
  }
  await p.waitForTimeout(80);
  await p.mouse.up();
  await p.waitForTimeout(600);
  await p.locator('button.el-button--primary').first().click();
  await p.waitForTimeout(6000);
  return p.evaluate(() => location.pathname);
}

const msgs = (p) => p.evaluate(() =>
  Array.from(document.querySelectorAll('.el-message, .el-notification, .el-form-item__error'))
    .map((x) => (x.innerText || '').replace(/\s+/g, ' ').trim()).filter(Boolean));

const dlgState = (p) => p.evaluate(() => {
  const d = Array.from(document.querySelectorAll('.el-dialog, .el-drawer')).find((x) => x.offsetParent !== null);
  return d ? {
    title: (d.querySelector('.el-dialog__title, .el-drawer__title')?.innerText || '').trim(),
    buttons: Array.from(d.querySelectorAll('button')).map((x) => (x.innerText || '').replace(/\s+/g, ' ').trim()).filter(Boolean),
  } : null;
});

/** 安全关闭当前弹窗：优先「取消」，其次「关闭」，再退化为 Esc。 */
async function closeDialog(p) {
  const scope = p.locator('.el-dialog, .el-drawer').last();
  for (const label of ['\u53D6\u6D88', '\u5173\u95ED']) {
    const loc = scope.locator('button', { hasText: label }).first();
    if (await loc.count()) { await loc.click().catch(() => {}); await p.waitForTimeout(800); break; }
  }
  if (await dlgState(p)) { await p.keyboard.press('Escape'); await p.waitForTimeout(700); }
}

// ---- A) /edu/question 批量导入弹窗 ----
async function caseA(p) {
  await p.goto(BASE + '/edu/question', { waitUntil: 'domcontentloaded' });
  await p.waitForSelector('main.el-main', { timeout: 60000 });
  await p.waitForTimeout(3000);

  const openBtn = p.locator('button, .el-button', { hasText: '\u6279\u91CF\u5BFC\u5165' }).first();
  await openBtn.click();
  await p.waitForTimeout(1200);
  const before = await p.evaluate(() => {
    const d = Array.from(document.querySelectorAll('.el-dialog')).find((x) => x.offsetParent !== null);
    const ta = d && d.querySelector('textarea');
    return { hasTextarea: !!ta, textareaLen: ta ? ta.value.length : -1 };
  });
  // 空输入点「导入」-> 期望业务校验，不写数据
  const importBtn = p.locator('.el-dialog').last().locator('button', { hasText: '\u5BFC\u5165' }).first();
  if (await importBtn.count()) {
    await importBtn.click();
    await p.waitForTimeout(1500);
  }
  log('A1) 批量导入 空点「导入」:', JSON.stringify({ before, msgs: await msgs(p) }));
  await closeDialog(p);
  log('A2) 批量导入 取消/关闭 ->', JSON.stringify({ dialog: await dlgState(p), msgs: await msgs(p) }));
}

// ---- B/C) /edu/students 教师行图标按钮 ----
async function openTeacherRow(p) {
  await p.goto(BASE + '/edu/students', { waitUntil: 'domcontentloaded' });
  await p.waitForSelector('main.el-main', { timeout: 60000 });
  await p.waitForTimeout(4000);
  await p.locator('.el-tabs__item', { hasText: '\u6559\u5E08' }).first().click();
  await p.waitForTimeout(5000);
  return p.evaluate(() => {
    const row = Array.from(document.querySelectorAll('tbody tr')).find((r) => r.offsetParent !== null);
    if (!row) return [];
    return Array.from(row.querySelectorAll('button')).map((e) => {
      const r = e.getBoundingClientRect();
      return { label: e.getAttribute('aria-label') || '', x: Math.round(r.x + r.width / 2), y: Math.round(r.y + r.height / 2) };
    });
  });
}

async function caseB(p) {
  const actions = await openTeacherRow(p);
  log('B0) 教师行操作:', JSON.stringify(actions.map((a) => a.label)));
  const rst = actions.find((a) => /\u91CD\u7F6E.*\u53E3\u4EE4/.test(a.label));
  if (!rst) { log('B) 未找到「重置登录口令」按钮'); return; }
  await p.mouse.click(rst.x, rst.y);
  await p.waitForTimeout(1500);
  log('B1) 重置登录口令 弹窗:', JSON.stringify(await dlgState(p)));
  await closeDialog(p);
  log('B2) 取消 ->', JSON.stringify({ dialog: await dlgState(p), msgs: await msgs(p) }));
}

async function caseC(p) {
  const actions = await openTeacherRow(p);
  const ed = actions.find((a) => a.label === '\u7F16\u8F91');
  if (!ed) { log('C) 未找到「编辑」按钮'); return; }
  await p.mouse.click(ed.x, ed.y);
  await p.waitForTimeout(1500);
  log('C1) 编辑 弹窗:', JSON.stringify(await dlgState(p)));
  await closeDialog(p);
  log('C2) 取消 ->', JSON.stringify({ dialog: await dlgState(p), msgs: await msgs(p) }));
}

// ---- D) /home 退出登录 + 重新登回 ----
async function caseD(p) {
  await p.goto(BASE + '/home', { waitUntil: 'domcontentloaded' });
  await p.waitForSelector('main.el-main', { timeout: 60000 });
  await p.waitForTimeout(2500);
  const trig = await p.evaluate(() => {
    const el = Array.from(document.querySelectorAll('*')).find((e) => {
      const r = e.getBoundingClientRect();
      return /\buser-container\b/.test(String(e.className)) && r.top >= 0 && r.top < 70 && r.width > 0;
    });
    if (!el) return null;
    const r = el.getBoundingClientRect();
    return { x: Math.round(r.x + r.width / 2), y: Math.round(r.y + r.height / 2) };
  });
  if (!trig) { log('D) 未找到顶栏 .user-container'); return; }
  await p.mouse.move(trig.x, trig.y);
  await p.waitForTimeout(300);
  await p.mouse.click(trig.x, trig.y);
  await p.waitForTimeout(1500);
  const hasLogout = await p.evaluate(() => Array.from(document.querySelectorAll('.el-popper, .el-dropdown__popper, [role="menu"]'))
    .some((x) => x.offsetParent !== null && (x.innerText || '').includes('\u9000\u51FA\u767B\u5F55')));
  log('D1) 下拉含「退出登录」:', hasLogout);
  if (!hasLogout) return;
  const tok = () => p.evaluate(() => Object.keys(localStorage).filter((k) => /token/i.test(k)).length);
  const tokBefore = await tok();
  await p.locator('text=\u9000\u51FA\u767B\u5F55').first().click();
  await p.waitForTimeout(5000);
  const after = await p.evaluate(() => ({ href: location.href, tok: Object.keys(localStorage).filter((k) => /token/i.test(k)).length }));
  log('D2) 退出登录: ', JSON.stringify({ tokBefore, after }));
  log('D3) 重新登录 ->', await loginAsAdmin(p));
}

(async () => {
  const b = await pw.chromium.launch({ headless: true });
  const ctx = await b.newContext({ viewport: { width: 1440, height: 900 } });
  const p = await ctx.newPage();
  const errs = [];
  p.on('pageerror', (e) => errs.push('PAGEERROR: ' + String(e.message).slice(0, 160)));
  p.on('console', (m) => { if (m.type() === 'error') errs.push('CONSOLE: ' + String(m.text()).slice(0, 160)); });

  try {
    log('### login (admin) ->', await loginAsAdmin(p));
    if (!ONLY || ONLY === 'a') { log('### A'); await caseA(p); }
    if (!ONLY || ONLY === 'b') { log('### B'); await caseB(p); }
    if (!ONLY || ONLY === 'c') { log('### C'); await caseC(p); }
    if (!ONLY || ONLY === 'd') { log('### D'); await caseD(p); }
  } catch (e) {
    log('PARTIAL FAILURE: ' + String(e.message).split('\n')[0]);
  }
  log('errors:', JSON.stringify(errs.slice(0, 8)));
  await b.close();
})().catch((e) => { console.error('FATAL', e.stack || e); process.exit(1); });
