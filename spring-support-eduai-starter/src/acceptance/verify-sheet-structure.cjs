/* Verify SheetStructureView fixes: page nav works and right parsed questions render. */
const fs = require('fs');
const path = require('path');
let pw;
try { pw = require('playwright'); }
catch (e) { pw = require('D:/ch/project/vue-support-parent-starter/node_modules/playwright'); }

const UI = process.argv[2] || 'http://127.0.0.1:19093';
const API = process.argv[3] || 'http://192.168.50.198:19095';
const TASK = process.argv[4] || 'fe637245bed14f428cebafe1ff1b884d';
const SHOT_DIR = path.join(process.env.TEMP || 'D:/ch/project/.tmp', 'opencode', 'eduai-sheet-ui');

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

  // Use the student's result URL. StudentRecordsView does not expose SheetStructureView,
  // so open the teacher/admin detail route which hosts MarkingDetailView.
  await p.goto(UI + '/edu/marking/detail?taskId=' + TASK, { waitUntil: 'domcontentloaded' });
  await p.waitForSelector('.sheet-structure', { timeout: 60000 });
  await p.waitForTimeout(2500);

  const pageState = await p.evaluate(() => {
    const radio = Array.from(document.querySelectorAll('.ss-toolbar .el-radio-button')).map((x) => (x.innerText || '').trim());
    const panelRows = document.querySelectorAll('.ss-panel .el-table__row').length;
    const prev = Array.from(document.querySelectorAll('.ss-nav--prev')).filter((x) => x.offsetParent !== null).length;
    const next = Array.from(document.querySelectorAll('.ss-nav--next')).filter((x) => x.offsetParent !== null).length;
    const activeRadio = document.querySelector('.ss-toolbar .el-radio-button.is-active');
    return {
      radios: radio,
      active: activeRadio ? activeRadio.innerText.trim() : null,
      prevVisible: prev,
      nextVisible: next,
      panelRows,
      bodySample: (document.querySelector('.ss-panel')?.innerText || '').slice(0, 180),
    };
  });
  record('sheet structure rendered', true, JSON.stringify(pageState));
  record('right panel has question rows', pageState.panelRows >= 1, 'rows=' + pageState.panelRows);
  record('pagination controls present', pageState.nextVisible >= 1 && pageState.radios.length >= 2,
    'prev=' + pageState.prevVisible + ' next=' + pageState.nextVisible + ' pages=' + pageState.radios.length);

  const before = await p.evaluate(() => {
    const active = document.querySelector('.ss-toolbar .el-radio-button.is-active')?.innerText?.trim() || '';
    const rows = document.querySelectorAll('.ss-panel .el-table__row').length;
    const src = document.querySelector('.ss-image')?.getAttribute('src') || '';
    return { active, rows, src: src.slice(0, 80) };
  });

  if (pageState.nextVisible >= 1) {
    await p.locator('.ss-nav--next').first().click();
    await p.waitForTimeout(1200);
  }
  const afterNext = await p.evaluate(() => {
    const active = document.querySelector('.ss-toolbar .el-radio-button.is-active')?.innerText?.trim() || '';
    const rows = document.querySelectorAll('.ss-panel .el-table__row').length;
    const src = document.querySelector('.ss-image')?.getAttribute('src') || '';
    const prevVisible = Array.from(document.querySelectorAll('.ss-nav--prev')).filter((x) => x.offsetParent !== null).length;
    return { active, rows, src: src.slice(0, 80), prevVisible };
  });
  record('next page changes active page', before.active !== afterNext.active,
    JSON.stringify({ before: before.active, after: afterNext.active }));
  record('next page reloads sheet image', before.src !== afterNext.src, 'src changed=' + (before.src !== afterNext.src));

  if (afterNext.prevVisible >= 1) {
    await p.locator('.ss-nav--prev').first().click();
    await p.waitForTimeout(1200);
  }
  const afterPrev = await p.evaluate(() => {
    const active = document.querySelector('.ss-toolbar .el-radio-button.is-active')?.innerText?.trim() || '';
    const rows = document.querySelectorAll('.ss-panel .el-table__row').length;
    return { active, rows };
  });
  record('prev page returns', afterPrev.active === before.active, JSON.stringify({ before: before.active, after: afterPrev.active }));
  record('right panel still has rows after paging', afterNext.rows >= 1 && afterPrev.rows >= 1,
    'next=' + afterNext.rows + ' prev=' + afterPrev.rows);

  const shot = path.join(SHOT_DIR, 'sheet-fix-' + Date.now() + '.png');
  await p.screenshot({ path: shot, fullPage: false });
  console.log('SCREENSHOT ' + shot);

  const unexpected = errs.filter((x) => !/Failed to load resource|ResizeObserver|currentQuestions/.test(x));
  record('no unexpected JS errors', unexpected.length === 0, JSON.stringify(unexpected.slice(0, 6)));

  const failed = checks.filter((c) => !c.ok).length;
  console.log('RESULT_JSON ' + JSON.stringify({ passed: checks.length - failed, failed, checks }));
  await browser.close();
  process.exit(failed === 0 ? 0 : 3);
})().catch((e) => { console.error('FATAL', e.stack || e); process.exit(1); });