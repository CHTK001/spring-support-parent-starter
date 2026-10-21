/* Full acceptance for SheetStructureView UI fix (paging + per-page parsed questions). */
const fs = require('fs');
const path = require('path');
let pw;
try { pw = require('playwright'); }
catch (e) { pw = require('D:/ch/project/vue-support-parent-starter/node_modules/playwright'); }

const UI = process.argv[2] || 'http://127.0.0.1:19093';
const API = process.argv[3] || 'http://192.168.50.198:19095';
const TASK = process.argv[4] || 'fe637245bed14f428cebafe1ff1b884d';
const STUDENT = { account: '2026000001', pass: 'Student@9043New' };
const SHOT_DIR = path.join(process.env.TEMP || 'D:/ch/project/.tmp', 'opencode', 'eduai-sheet-ui-full');
const EXPECT_PAGE_COUNTS = [5, 5, 6, 10, 12, 5];

const checks = [];
const record = (name, ok, detail) => {
  checks.push({ name, ok: !!ok, detail: String(detail || '') });
  console.log((ok ? '  PASS  ' : '  FAIL  ') + name + (ok ? '' : '   [' + detail + ']'));
};

async function login(p) {
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
  return p.evaluate(() => location.pathname);
}

async function structureSummary(token) {
  const res = await fetch(API + '/v2/edu/marking/structure/' + TASK, { headers: { Authorization: 'Bearer ' + token } });
  const body = await res.json();
  const d = body?.data || {};
  return {
    pageCount: Number(d.pageCount || 0),
    anchors: (d.questionAnchors || []).length,
    questions: (d.questions || []).length,
  };
}

(async () => {
  fs.mkdirSync(SHOT_DIR, { recursive: true });
  const browser = await pw.chromium.launch({ headless: true });
  const ctx = await browser.newContext({ viewport: { width: 1600, height: 1000 } });
  const p = await ctx.newPage();
  const errs = [];
  const badResp = [];
  p.on('pageerror', (e) => errs.push(String(e.message || e).slice(0, 200)));
  p.on('console', (m) => { if (m.type() === 'error') errs.push(String(m.text()).slice(0, 200)); });
  p.on('response', (r) => { if (r.status() >= 400) badResp.push(r.status() + ' ' + r.url().replace(/^https?:\/\/[^/]+/, '')); });

  // 1. API 健康与结构数据
  const healthRes = await fetch(API + '/actuator/health');
  record('backend health 200', healthRes.status === 200, 'http=' + healthRes.status);

  const loginRes = await fetch(API + '/v2/user/login', {
    method: 'POST', headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ username: STUDENT.account, password: STUDENT.pass, authType: 'STUDENT', accountType: 'student' }),
  });
  const loginBody = await loginRes.json();
  const token = loginBody?.data?.accessToken || '';
  record('student API login ok', !!token, 'len=' + token.length);

  const st = await structureSummary(token);
  record('structure API complete', st.pageCount === 6 && st.anchors === 43 && st.questions === 43,
    'pages=' + st.pageCount + ' anchors=' + st.anchors + ' questions=' + st.questions);

  // 2. 浏览器登录 + 详情页
  const pathAfter = await login(p);
  record('browser login landed', pathAfter.includes('/edu/student'), pathAfter);

  await p.goto(UI + '/edu/marking/detail?taskId=' + TASK, { waitUntil: 'domcontentloaded' });
  await p.waitForSelector('.sheet-structure', { timeout: 60000 });
  await p.waitForTimeout(2500);

  const baseState = await p.evaluate(() => ({
    modeRadio: Array.from(document.querySelectorAll('.ss-mode .el-radio-button')).map((x) => (x.innerText || '').trim()),
    pager: document.querySelector('.ss-pager__text')?.innerText?.trim() || '',
    stagePage: document.querySelector('.ss-stage-page')?.innerText?.trim() || '',
    hasImage: !!document.querySelector('.ss-image')?.getAttribute('src'),
    anchors: document.querySelectorAll('.ss-anchor').length,
    panelRows: document.querySelectorAll('.ss-panel .el-table__row').length,
    hasSave: !!Array.from(document.querySelectorAll('button')).find((b) => (b.innerText || '').includes('保存老师分')),
    panelText: (document.querySelector('.ss-panel')?.innerText || '').replace(/\s+/g, ' ').slice(0, 200),
  }));
  record('detail page rendered', baseState.hasImage && baseState.hasSave, JSON.stringify({
    hasImage: baseState.hasImage, hasSave: baseState.hasSave, anchors: baseState.anchors,
  }));
  record('mode + pager controls present', baseState.modeRadio.includes('版面解析') && baseState.modeRadio.includes('原始试卷') && !!baseState.pager,
    JSON.stringify({ mode: baseState.modeRadio, pager: baseState.pager, stagePage: baseState.stagePage }));
  record('page 1 has anchors', baseState.anchors >= EXPECT_PAGE_COUNTS[0], 'anchors=' + baseState.anchors);
  record('page 1 right panel rows', baseState.panelRows === EXPECT_PAGE_COUNTS[0], 'rows=' + baseState.panelRows);
  record('right panel has parsed content', baseState.panelText.length > 40, 'text=' + baseState.panelText.slice(0, 80));

  // 3. 逐页切换 + 题目数量 + 题号唯一
  const observed = [];
  for (let i = 1; i <= 6; i++) {
    const state = await p.evaluate(() => {
      const rows = Array.from(document.querySelectorAll('.ss-panel .el-table__row')).map((r) => (r.innerText || '').replace(/\s+/g, ' ').trim());
      const nums = rows.map((x) => { const m = x.match(/^([0-9]+)\./); return m ? Number(m[1]) : null; }).filter((x) => x != null);
      const img = document.querySelector('.ss-image')?.getAttribute('src') || '';
      return { rows: rows.length, nums, img };
    });
    observed.push(state);
    record(`page ${i} shows expected questions`, state.rows === EXPECT_PAGE_COUNTS[i - 1] && new Set(state.nums).size === state.nums.length,
      `rows=${state.rows} expected=${EXPECT_PAGE_COUNTS[i - 1]} first=${state.nums.slice(0, 2).join(',')}`);
    if (i < 6) {
      await p.locator('.ss-nav--next').first().click();
      await p.waitForTimeout(1200);
    }
  }
  record('images switch per page', observed.slice(1).every((s, i) => s.img !== observed[i].img), 'changed=' + observed.filter((s) => s.img).length);
  record('all 43 question numbers unique across pages', new Set(observed.flatMap((s) => s.nums)).size === 43,
    'paginated=' + observed.map((s) => s.rows).join('/'));

  await p.locator('.ss-nav--prev').first().click();
  await p.waitForTimeout(1000);
  const back = await p.evaluate(() => ({
    pageText: document.querySelector('.ss-stage-page')?.innerText?.trim() || '',
    rows: document.querySelectorAll('.ss-panel .el-table__row').length,
  }));
  record('prev page returns to page 5', back.pageText.includes('5') && back.rows === 12, JSON.stringify(back));

  const shot = path.join(SHOT_DIR, 'full-acceptance-' + Date.now() + '.png');
  await p.screenshot({ path: shot, fullPage: false });
  console.log('SCREENSHOT ' + shot);

  const unexpectedErr = errs.filter((x) => !/Failed to load resource|ResizeObserver|currentQuestions/.test(x));
  record('no unexpected JS errors', unexpectedErr.length === 0, JSON.stringify(unexpectedErr.slice(0, 6)));
  record('no unexpected HTTP >= 400', badResp.length === 0, JSON.stringify(badResp.slice(0, 6)));

  const failed = checks.filter((c) => !c.ok).length;
  console.log('RESULT_JSON ' + JSON.stringify({ passed: checks.length - failed, failed, checks }));
  await browser.close();
  process.exit(failed === 0 ? 0 : 3);
})().catch((e) => { console.error('FATAL', e.stack || e); process.exit(1); });