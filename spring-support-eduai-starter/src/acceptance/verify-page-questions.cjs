/* Verify page-specific parsed questions in SheetStructureView. */
const fs = require('fs');
const path = require('path');
let pw;
try { pw = require('playwright'); }
catch (e) { pw = require('D:/ch/project/vue-support-parent-starter/node_modules/playwright'); }

const UI = process.argv[2] || 'http://127.0.0.1:19093';
const API = process.argv[3] || 'http://192.168.50.198:19095';
const TASK = process.argv[4] || 'fe637245bed14f428cebafe1ff1b884d';
const SHOT_DIR = path.join(process.env.TEMP || 'D:/ch/project/.tmp', 'opencode', 'eduai-sheet-ui');
const EXPECT_PAGE_COUNTS = [5, 5, 6, 10, 12, 5];

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
  await p.locator('.sl-field__box input[type="text"]').first().fill('2026000001');
  await p.locator('.sl-field__box input[type="password"]').first().fill('Student@9043New');
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

  const observed = [];
  for (let i = 1; i <= 6; i++) {
    const state = await p.evaluate(() => {
      const active = document.querySelector('.ss-toolbar .el-radio-button.is-active')?.innerText?.trim() || '';
      const rows = Array.from(document.querySelectorAll('.ss-panel .el-table__row')).map((r) => (r.innerText || '').replace(/\s+/g, ' ').trim());
      const nums = rows.map((x) => { const m = x.match(/^([0-9]+)\./); return m ? Number(m[1]) : null; }).filter((x) => x != null);
      return { active, rows: rows.length, nums };
    });
    observed.push(state);
    const expected = EXPECT_PAGE_COUNTS[i - 1];
    const unique = new Set(state.nums).size === state.nums.length;
    const numeric = state.nums.length === expected && state.nums.every((n) => Number.isInteger(n) && n >= 1);
    record(`page ${i} shows expected questions`, numeric && unique, `active=${state.active} rows=${state.rows} expected=${expected} first=${state.nums.slice(0, 2).join(',')}`);
    if (i < 6) {
      await p.locator('.ss-nav--next').first().click();
      await p.waitForTimeout(1200);
    }
  }
  const noDuplicates = observed.every((s) => new Set(s.nums).size === s.nums.length);
  const globalUnique = new Set(observed.flatMap((s) => s.nums)).size === observed.flatMap((s) => s.nums).length;
  record('question numbers are unique across 6 pages', noDuplicates && globalUnique, 'paginated=' + observed.map((s) => s.rows).join('/'));
  await p.locator('.ss-nav--prev').first().click();
  await p.waitForTimeout(1000);
  const back = await p.evaluate(() => {
    const pageText = document.querySelector('.ss-stage-page')?.innerText?.trim() || '';
    const rows = document.querySelectorAll('.ss-panel .el-table__row').length;
    return { pageText, rows };
  });
  record('prev page returns to page 5', back.pageText.includes('5') && back.rows === 12, JSON.stringify(back));

  const shot = path.join(SHOT_DIR, 'page-specific-' + Date.now() + '.png');
  await p.screenshot({ path: shot, fullPage: false });
  console.log('SCREENSHOT ' + shot);

  const unexpected = errs.filter((x) => !/Failed to load resource|ResizeObserver|currentQuestions/.test(x));
  record('no unexpected JS errors', unexpected.length === 0, JSON.stringify(unexpected.slice(0, 6)));

  const failed = checks.filter((c) => !c.ok).length;
  console.log('RESULT_JSON ' + JSON.stringify({ passed: checks.length - failed, failed, checks }));
  await browser.close();
  process.exit(failed === 0 ? 0 : 3);
})().catch((e) => { console.error('FATAL', e.stack || e); process.exit(1); });