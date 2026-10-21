/*
 * Real paper marking browser acceptance.
 * Browser uploads the real sheet; result polling goes directly to the backend to avoid Vite page noise.
 */
const fs = require('fs');
const path = require('path');
let pw;
try { pw = require('playwright'); }
catch (e) { pw = require('D:/ch/project/vue-support-parent-starter/node_modules/playwright'); }

const UI = process.argv[2] || 'http://127.0.0.1:19093';
const API = process.argv[3] || 'http://192.168.50.198:19095';
const PAPER_ID = process.argv[4] || '9001';
const IMG = process.argv[5] || 'D:/ch/project/.tmp/real-paper-fc8.png';
const PAPER_LABEL = process.argv[6] || '\u9A8C\u6536\u7EC4\u5377-\u751F\u7269\u521D\u4E00';
const ADMIN = { user: 'admin', pass: 'admin123' };
const SHOT_DIR = path.join(process.env.TEMP || 'D:/ch/project/.tmp', 'opencode', 'eduai-realpaper');

const checks = [];
const record = (name, ok, detail) => {
  checks.push({ name, ok: !!ok, detail: String(detail || '') });
  console.log((ok ? '  PASS  ' : '  FAIL  ') + name + (ok ? '' : '   [' + detail + ']'));
};

async function loginAdmin(p) {
  await p.goto(UI + '/login', { waitUntil: 'domcontentloaded' });
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

async function loginToken() {
  const res = await fetch(API + '/v2/user/login', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ username: ADMIN.user, password: ADMIN.pass }),
  });
  const body = await res.json();
  return (body && body.data && body.data.accessToken) || '';
}

async function getResultRaw(token, id) {
  const res = await fetch(API + '/v2/edu/marking/result/' + id, {
    headers: { Authorization: 'Bearer ' + token },
  });
  const txt = await res.text();
  try { return JSON.parse(txt); } catch (e) { return { raw: txt.slice(0, 200) }; }
}

(async () => {
  if (!fs.existsSync(IMG)) throw new Error('image missing: ' + IMG);
  fs.mkdirSync(SHOT_DIR, { recursive: true });
  const browser = await pw.chromium.launch({ headless: true });
  const ctx = await browser.newContext({ viewport: { width: 1440, height: 900 } });
  const p = await ctx.newPage();
  const errs = [];
  const badResp = [];
  let scanTaskId = '';
  let scanResponse = null;
  p.on('pageerror', (e) => errs.push(String(e.message || e).slice(0, 160)));
  p.on('console', (m) => { if (m.type() === 'error') errs.push(String(m.text()).slice(0, 160)); });
  p.on('response', async (r) => {
    if (r.status() >= 400) badResp.push(r.status() + ' ' + r.request().method() + ' ' + r.url().replace(/^https?:\/\/[^/]+/, ''));
    if (r.url().includes('/v2/edu/marking/scan') && r.request().method() === 'POST') {
      try {
        const body = await r.json();
        scanResponse = body;
        const data = body && body.data;
        if (data) scanTaskId = String(data);
      } catch (e) { /* ignore */ }
    }
  });

  console.log('=== login admin ===');
  const pathAfter = await loginAdmin(p);
  await p.evaluate(() => {
    const ds = Array.from(document.querySelectorAll('.el-dialog'));
    for (const d of ds) {
      const txt = (d.innerText || '').replace(/\s+/g, ' ').trim();
      if (txt.includes('\u534F\u8BAE') || txt.includes('\u540C\u610F')) {
        const btns = Array.from(d.querySelectorAll('button'));
        const b = btns.find((x) => (x.innerText || '').includes('\u540C\u610F')) || btns.find((x) => (x.innerText || '').includes('\u786E\u5B9A'));
        if (b) b.click();
      }
    }
  });
  await p.waitForTimeout(1000);
  record('admin login left /login', !pathAfter.includes('/login'), pathAfter);

  console.log('=== scan real paper ===');
  await p.goto(UI + '/edu/marking', { waitUntil: 'domcontentloaded' });
  await p.waitForSelector('.marking-workbench', { timeout: 60000 });
  await p.waitForTimeout(2500);
  await p.getByRole('button', { name: '\u626B\u63CF\u9605\u5377' }).first().click();
  await p.waitForTimeout(1200);
  const dlg = p.locator('.el-dialog').filter({ hasText: '\u4E0A\u4F20\u7B54\u5377\u9605\u5377' }).last();
  await dlg.waitFor({ state: 'visible', timeout: 30000 });
  record('scan dialog opened', true, 'title=upload-marking');

  const paperSelect = dlg.locator('.el-form-item').filter({ has: p.locator('.el-select') }).first().locator('.el-select').first();
  await paperSelect.click();
  await p.waitForSelector('.el-select-dropdown:visible .el-select-dropdown__item', { timeout: 15000 });
  const option = p.locator('.el-select-dropdown:visible .el-select-dropdown__item').filter({ hasText: PAPER_LABEL }).first();
  record('paper 9001 option shown', (await option.count()) > 0, 'paperId=' + PAPER_ID);
  await option.click();
  await p.waitForTimeout(600);

  await dlg.locator('input[type="file"]').setInputFiles(IMG);
  await p.waitForTimeout(800);
  const fileCount = await dlg.locator('.mu-list__item').count();
  record('real paper file attached', fileCount >= 1, 'files=' + fileCount + ' img=' + IMG);

  const scanRespPromise = p.waitForResponse((r) => r.url().includes('/v2/edu/marking/scan') && r.request().method() === 'POST', { timeout: 90000 });
  await dlg.getByRole('button', { name: '\u5F00\u59CB\u9605\u5377' }).click();
  const scanResp = await scanRespPromise;
  try {
    const body = await scanResp.json();
    scanResponse = body;
    const data = body && body.data;
    if (data) scanTaskId = String(data);
  } catch (e) { /* ignore */ }
  await p.waitForTimeout(500);
  record('scan submit returned taskId', !!scanTaskId, scanTaskId + ' resp=' + JSON.stringify(scanResponse || {}).slice(0, 120));

  const token = await loginToken();
  record('API login token for result polling', token.length > 20, 'len=' + token.length);

  console.log('=== wait for result (max 180s) ===');
  let payload = null;
  const deadline = Date.now() + 180000;
  while (Date.now() < deadline) {
    try {
      const body = await getResultRaw(token, scanTaskId);
      const data = body && body.data;
      if (data && (data.stage === '\u5DF2\u5B8C\u6210' || data.finished === true)) {
        payload = data;
        break;
      }
      if (data && data.stage === '\u5931\u8D25') { payload = data; break; }
    } catch (e) { /* transient */ }
    await new Promise((r) => setTimeout(r, 3000));
  }
  record('marking completed', !!payload && (payload.stage === '\u5DF2\u5B8C\u6210' || payload.finished === true), payload && JSON.stringify({ stage: payload.stage, finished: payload.finished, total: payload.total }).slice(0, 160));

  if (payload) {
    const rs = payload.results || [];
    const real = rs.filter((r) => Number(r.questionId || 0) > 0);
    const scored = real.filter((r) => Number(r.score || 0) > 0);
    const zero = real.filter((r) => Number(r.score || 0) === 0);
    const comments = real.filter((r) => String(r.comment || '').trim()).length;
    record('parsed question-level results', real.length >= 1, 'real_q=' + real.length);
    record('has correct scored answers', scored.length >= 1, 'scored=' + scored.length);
    record('has wrong/zero answers', zero.length >= 1, 'zero=' + zero.length);
    record('has marking comments', comments >= 1, 'comments=' + comments);
    console.log('SAMPLE ' + JSON.stringify(real.map((r) => ({ qid: r.questionId, score: r.score, max: r.maxScore, comment: String(r.comment || '').slice(0, 30) })).slice(0, 8)));

    await p.goto(UI + '/edu/marking/detail?taskId=' + scanTaskId, { waitUntil: 'domcontentloaded' });
    await p.waitForSelector('.marking-detail', { timeout: 60000 });
    await p.waitForTimeout(2500);
    const shot = path.join(SHOT_DIR, 'realpaper-detail-' + Date.now() + '.png');
    await p.screenshot({ path: shot, fullPage: false });
    console.log('SCREENSHOT ' + shot);
  }

  const unexpectedErr = errs.filter((x) => !/Failed to load resource|ResizeObserver|currentQuestions/.test(x));
  const unexpectedBad = badResp.filter((x) => !x.includes('/v2/edu/marking/scan'));
  record('no unexpected JS errors', unexpectedErr.length === 0, JSON.stringify(unexpectedErr.slice(0, 4)));
  record('no unexpected HTTP >= 400', unexpectedBad.length === 0, JSON.stringify(unexpectedBad.slice(0, 4)));

  const failed = checks.filter((c) => !c.ok).length;
  console.log('RESULT_JSON ' + JSON.stringify({ passed: checks.length - failed, failed, checks }));
  await browser.close();
  process.exit(failed === 0 ? 0 : 3);
})().catch((e) => { console.error('FATAL', e.stack || e); process.exit(1); });