/* Verify teacher comment stays empty (real teacher only) while AI comment column keeps AI text. */
const fs = require('fs');
const path = require('path');
let pw;
try { pw = require('playwright'); }
catch (e) { pw = require('D:/ch/project/vue-support-parent-starter/node_modules/playwright'); }

const UI = process.argv[2] || 'http://127.0.0.1:19093';
const API = process.argv[3] || 'http://192.168.50.198:19095';
const TASK = process.argv[4] || 'fe637245bed14f428cebafe1ff1b884d';
const STUDENT = { account: '2026000001', pass: 'Student@9043New' };
const SHOT_DIR = path.join(process.env.TEMP || 'D:/ch/project/.tmp', 'opencode', 'eduai-sheet-teacher-comment');

const checks = [];
const record = (name, ok, detail) => {
  checks.push({ name, ok: !!ok, detail: String(detail || '') });
  console.log((ok ? '  PASS  ' : '  FAIL  ') + name + (ok ? '' : '   [' + detail + ']'));
};

(async () => {
  fs.mkdirSync(SHOT_DIR, { recursive: true });
  const browser = await pw.chromium.launch({ headless: true });
  const ctx = await browser.newContext({ viewport: { width: 1600, height: 1000 } });
  const p = await ctx.newPage();
  const errs = [];
  p.on('pageerror', (e) => errs.push(String(e.message || e).slice(0, 200)));
  p.on('console', (m) => { if (m.type() === 'error') errs.push(String(m.text()).slice(0, 200)); });

  await p.goto(UI + '/edu/student-login', { waitUntil: 'domcontentloaded' });
  await p.waitForSelector('.sl-form input[type="text"]');
  await p.locator('.sl-field__box input[type="text"]').first().fill(STUDENT.account);
  await p.locator('.sl-field__box input[type="password"]').first().fill(STUDENT.pass);
  await p.locator('button.sl-submit').first().click();
  await p.waitForTimeout(5000);
  const dlg = p.locator('.el-dialog').filter({ hasText: '首次登录：请修改初始密码' }).last();
  if (await dlg.count()) {
    await dlg.locator('input[type="password"]').nth(1).fill('Student@9043New');
    await dlg.getByRole('button', { name: '设置我的密码' }).click();
    await p.waitForTimeout(2000);
  }

  await p.goto(UI + '/edu/marking/detail?taskId=' + TASK, { waitUntil: 'domcontentloaded' });
  await p.waitForSelector('.sheet-structure', { timeout: 60000 });
  await p.waitForTimeout(2500);

  const rowState = await p.evaluate(() => {
    const headers = Array.from(document.querySelectorAll('.ss-panel thead th')).map((x) => (x.innerText || '').trim());
    const aiIdx = headers.indexOf('AI 评语');
    const tcIdx = headers.indexOf('老师评语');
    const first = document.querySelector('.ss-panel .el-table__row');
    if (!first) return { headers, aiIdx, tcIdx, missing: true };
    const cells = Array.from(first.querySelectorAll('td')).map((x) => (x.innerText || '').replace(/\s+/g, ' ').trim());
    const ai = aiIdx >= 0 ? cells[aiIdx] : '';
    const tc = tcIdx >= 0 ? cells[tcIdx] : '';
    const icon = first.querySelector('.ss-comment-edit');
    return { headers, aiIdx, tcIdx, ai, tc, iconClass: icon ? String(icon.className) : '', missing: false };
  });
  record('columns AI评语/老师评语 present', rowState.aiIdx >= 0 && rowState.tcIdx >= 0, JSON.stringify(rowState.headers));
  record('AI comment column keeps AI text', rowState.ai.includes('选择错误') || rowState.ai.includes('与标准答案') || rowState.ai.length > 0, 'ai=' + rowState.ai.slice(0, 60));
  record('teacher comment is empty (—)', rowState.tc === '—', 'tc=' + rowState.tc);
  record('teacher comment edit icon unfilled', !rowState.iconClass.includes('--filled'), 'class=' + rowState.iconClass);

  const shot = path.join(SHOT_DIR, 'teacher-comment-' + Date.now() + '.png');
  await p.screenshot({ path: shot, fullPage: false });
  console.log('SCREENSHOT ' + shot);

  const unexpected = errs.filter((x) => !/Failed to load resource|ResizeObserver|currentQuestions/.test(x));
  record('no unexpected JS errors', unexpected.length === 0, JSON.stringify(unexpected.slice(0, 6)));

  const failed = checks.filter((c) => !c.ok).length;
  console.log('RESULT_JSON ' + JSON.stringify({ passed: checks.length - failed, failed, checks }));
  await browser.close();
  process.exit(failed === 0 ? 0 : 3);
})().catch((e) => { console.error('FATAL', e.stack || e); process.exit(1); });