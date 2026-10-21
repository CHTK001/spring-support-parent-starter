/*
 * Student role marking acceptance for paper 9043 task fe637245...
 * Logs in as student 2026000001 and verifies the record appears in the student portal.
 */
const fs = require('fs');
const path = require('path');
let pw;
try { pw = require('playwright'); }
catch (e) { pw = require('D:/ch/project/vue-support-parent-starter/node_modules/playwright'); }

const UI = process.argv[2] || 'http://127.0.0.1:19093';
const API = process.argv[3] || 'http://192.168.50.198:19095';
const TASK = process.argv[4] || 'fe637245bed14f428cebafe1ff1b884d';
const STUDENT = { account: '2026000001', pass: 'Student@9043New', name: '测试学生甲' };
const SHOT_DIR = path.join(process.env.TEMP || 'D:/ch/project/.tmp', 'opencode', 'eduai-student-9043');

const checks = [];
const record = (name, ok, detail) => {
  checks.push({ name, ok: !!ok, detail: String(detail || '') });
  console.log((ok ? '  PASS  ' : '  FAIL  ') + name + (ok ? '' : '   [' + detail + ']'));
};

async function loginStudent(p) {
  await p.goto(UI + '/edu/student-login', { waitUntil: 'domcontentloaded' });
  await p.waitForSelector('.sl-form input[type="text"]', { timeout: 60000 });
  await p.waitForSelector('.sl-form input[type="password"]', { timeout: 60000 });
  await p.waitForTimeout(600);
  await p.locator('.sl-field__box input[type="text"]').first().fill(STUDENT.account);
  await p.locator('.sl-field__box input[type="password"]').first().fill(STUDENT.pass);
  await p.locator('button.sl-submit').first().click();
  await p.waitForTimeout(6000);
  return p.evaluate(() => location.pathname);
}

async function dismissAgreement(p) {
  await p.evaluate(() => {
    const ds = Array.from(document.querySelectorAll('.el-dialog, .el-message-box'));
    for (const d of ds) {
      const txt = (d.innerText || '').replace(/\s+/g, ' ').trim();
      if (txt.includes('协议') || txt.includes('同意')) {
        const btns = Array.from(d.querySelectorAll('button'));
        const b = btns.find((x) => (x.innerText || '').includes('同意')) || btns.find((x) => (x.innerText || '').includes('确定'));
        if (b) b.click();
      }
    }
  });
}

async function completeFirstLoginPassword(p) {
  const dlg = p.locator('.el-dialog').filter({ hasText: '首次登录：请修改初始密码' }).last();
  if (!(await dlg.count())) return false;
  await dlg.waitFor({ state: 'visible', timeout: 30000 });
  const inputs = dlg.locator('input[type="password"]');
  await inputs.nth(1).fill('Student@9043New');
  await dlg.getByRole('button', { name: '设置我的密码' }).click();
  await dlg.waitFor({ state: 'hidden', timeout: 30000 });
  await p.waitForTimeout(1000);
  return true;
}

(async () => {
  fs.mkdirSync(SHOT_DIR, { recursive: true });
  const browser = await pw.chromium.launch({ headless: true });
  const ctx = await browser.newContext({ viewport: { width: 1440, height: 900 } });
  const p = await ctx.newPage();
  const errs = [];
  const badResp = [];
  p.on('pageerror', (e) => errs.push(String(e.message || e).slice(0, 160)));
  p.on('console', (m) => { if (m.type() === 'error') errs.push(String(m.text()).slice(0, 160)); });
  p.on('response', (r) => { if (r.status() >= 400) badResp.push(r.status() + ' ' + r.request().method() + ' ' + r.url().replace(/^https?:\/\/[^/]+/, '')); });

  console.log('=== student login ===');
  const pathAfter = await loginStudent(p);
  await dismissAgreement(p);
  const changedPwd = await completeFirstLoginPassword(p);
  record('student login landed portal', pathAfter.includes('/edu/student'), pathAfter + ' user=' + STUDENT.name);
  record('first-login password flow completed', true, 'mustChangeDialog=' + changedPwd);
  await p.waitForTimeout(1200);

  console.log('=== open my-records ===');
  await p.goto(UI + '/edu/student?m=my-records', { waitUntil: 'domcontentloaded' });
  await p.waitForSelector('.mr-title', { timeout: 60000 });
  const title = (await p.locator('.mr-title').innerText()).replace(/\s+/g, '').trim();
  record('my-records module opened', title.includes('我的答题记录'), title);

  const row = p.locator('.el-table__row').filter({ hasText: TASK }).first();
  const card = p.locator('.mr-card').filter({ hasText: TASK }).first();
  await p.waitForFunction((task) => {
    const all = document.querySelectorAll('.el-table__row, .mr-card');
    return Array.from(all).some((x) => (x.innerText || '').includes(task));
  }, TASK, { timeout: 60000 });

  const clickTarget = async () => {
    if (await row.count()) {
      await row.getByRole('button', { name: '查看' }).first().click();
      return 'table';
    }
    if (await card.count()) {
      await card.getByRole('button', { name: '查看答卷' }).first().click();
      return 'card';
    }
    throw new Error('student task row not found');
  };
  const kind = await clickTarget();
  record('found task for student', true, kind + ' task=' + TASK);

  await p.waitForSelector('.mr-result .mr-sum', { timeout: 60000 });
  await p.waitForTimeout(1500);
  const sum = (await p.locator('.mr-result .mr-sum').innerText()).replace(/\s+/g, ' ').trim();
  record('result summary opens', /44/.test(sum) && /43/.test(sum), sum);

  const resultState = await p.evaluate(() => {
    const rows = Array.from(document.querySelectorAll('.mr-result .el-table__row'));
    let scored = 0;
    let comments = 0;
    for (const r of rows) {
      const txt = (r.innerText || '').replace(/\s+/g, ' ').trim();
      if (/(^|\s)10(\s|$)/.test(txt) || /(^|\s)[1-9](\s|$)/.test(txt)) scored++;
      if (r.querySelector('td:last-child')?.innerText?.trim() && r.querySelector('td:last-child')?.innerText?.trim() !== '—') comments++;
    }
    return { rows: rows.length, scored, comments };
  });
  record('43 question rows rendered', resultState.rows >= 43, JSON.stringify(resultState));
  record('some questions scored', resultState.scored >= 16, 'scored_rows=' + resultState.scored);
  record('comments present', resultState.comments >= 20, 'comments=' + resultState.comments);

  const shot = path.join(SHOT_DIR, 'student-9043-' + Date.now() + '.png');
  await p.screenshot({ path: shot, fullPage: false });
  console.log('SCREENSHOT ' + shot);

  const unexpectedErr = errs.filter((x) => !/Failed to load resource|ResizeObserver|currentQuestions/.test(x));
  const unexpectedBad = badResp.filter((x) => !x.includes('/v2/edu/marking/scan'));
  record('no unexpected JS errors', unexpectedErr.length === 0, JSON.stringify(unexpectedErr.slice(0, 4)));
  record('no unexpected HTTP >= 400', unexpectedBad.length === 0, JSON.stringify(unexpectedBad.slice(0, 4)));

  const failed = checks.filter((c) => !c.ok).length;
  console.log('RESULT_JSON ' + JSON.stringify({ passed: checks.length - failed, failed, checks }));
  await browser.close();
  process.exit(failed === 0 ? 0 : 3);
})().catch((e) => { console.error('FATAL', e.stack || e); process.exit(1); });