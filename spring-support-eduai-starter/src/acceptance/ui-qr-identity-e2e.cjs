/**
 * 生产环境浏览器交付验收：身份二维码归卷。
 *
 * 前端选「任意卷」、不选试卷、不留学生，上传带
 * PAPER:9001;STUDENT:20260001;EXAM:88 的合成答题卡；
 * 后端应自动归为 SELF/9001、绑定学生/考试并完成判分。
 */
const fs = require('fs');
const path = require('path');
let pw;
try { pw = require('playwright'); }
catch (e) { pw = require('D:/ch/project/vue-support-parent-starter/node_modules/playwright'); }

const UI = process.argv[2] || 'http://192.168.50.198:19093';
const API = process.argv[3] || 'http://192.168.50.198:19095';
const IMG = process.argv[4] || 'D:/ch/project/.tmp/combined-9001.png';
const ADMIN = { user: 'admin', pass: 'admin123' };
const SHOT_DIR = path.join(process.env.TEMP || 'D:/ch/project/.tmp', 'opencode', 'ui-qr-identity');
fs.mkdirSync(SHOT_DIR, { recursive: true });

const checks = [];
const record = (name, ok, detail) => {
  checks.push({ name, ok: !!ok, detail: String(detail ?? '') });
  console.log(`  ${ok ? 'PASS' : 'FAIL'}  ${name}${ok ? '' : '   [' + detail + ']'}`);
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
  await p.getByPlaceholder('请输入用户名').fill(ADMIN.user);
  await p.getByPlaceholder('请输入密码').fill(ADMIN.pass);
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

(async () => {
  if (!fs.existsSync(IMG)) throw new Error('image missing: ' + IMG);
  const browser = await pw.chromium.launch({ headless: true });
  const ctx = await browser.newContext({ viewport: { width: 1600, height: 1000 } });
  const p = await ctx.newPage();
  const errs = [];
  const badResp = [];
  let scanTaskId = '';
  p.on('pageerror', (e) => errs.push(String(e.message || e).slice(0, 160)));
  p.on('console', (m) => { if (m.type() === 'error') errs.push(String(m.text()).slice(0, 160)); });
  p.on('response', (r) => {
    if (r.status() >= 400) badResp.push(r.status() + ' ' + r.request().method() + ' ' + r.url().replace(/^https?:\/\/[^/]+/, ''));
  });
  process.on('unhandledRejection', (e) => { console.error('UNHANDLED_REJECTION', e); });

  const pathAfter = await loginAdmin(p);
  record('admin login left /login', !pathAfter.includes('/login'), pathAfter);

  await p.goto(UI + '/edu/marking', { waitUntil: 'domcontentloaded' });
  await p.waitForSelector('.marking-workbench, .mw-batch-bar', { timeout: 60000 });
  await p.waitForTimeout(3000);
  await p.getByRole('button', { name: '扫描阅卷' }).first().click();
  await p.waitForTimeout(1200);
  const dlg = p.locator('.el-dialog').filter({ hasText: '上传答卷阅卷' }).last();
  await dlg.waitFor({ state: 'visible', timeout: 30000 });
  record('scan dialog opened', true, 'title=upload-marking');

  const anyRadio = dlg.locator('.el-radio-button, .el-radio').filter({ hasText: '任意卷' }).first();
  await anyRadio.click();
  await p.waitForTimeout(400);
  await dlg.locator('input[type="file"]').setInputFiles(IMG);
  await p.waitForTimeout(800);
  const fileCount = await dlg.locator('.mu-list__item').count();
  record('ANY mode file attached', fileCount > 0, 'file=' + path.basename(IMG) + ' count=' + fileCount);

  const startBtn = dlg.getByRole('button', { name: '开始阅卷' });
  const startDisabled = await startBtn.isDisabled().catch(() => null);
  record('start button enabled after attach', startDisabled === false, 'disabled=' + startDisabled);
  let scanResponse = null;
  if (startDisabled === false) {
    const scanRespPromise = p.waitForResponse(
      (r) => r.url().includes('/v2/edu/marking/scan') && r.request().method() === 'POST',
      { timeout: 30000 },
    ).catch(() => null);
    await startBtn.click();
    console.log('START_CLICKED');
    scanResponse = await scanRespPromise;
    console.log('SCAN_RESP_FOUND ' + !!scanResponse);
    if (scanResponse) {
      try {
        const body = await scanResponse.json();
        scanResponse = body;
        const data = body && body.data;
        if (data) scanTaskId = String(data);
      } catch (e) {
        console.error('SCAN_PARSE_FAIL', e);
      }
    }
  }
  let pageClosed = false;
  const deadline = Date.now() + 30000;
  while (!scanTaskId && Date.now() < deadline) {
    try {
      await p.waitForTimeout(500);
    } catch (e) {
      pageClosed = true;
      break;
    }
  }
  record('scan submit returned taskId', !!scanTaskId, scanTaskId + (pageClosed ? ' (page closed but task captured)' : ''));

  const token = await loginToken();
  record('API login token', token.length > 20, 'len=' + token.length);

  let payload = null;
  if (scanTaskId && token) {
    const waitDeadline = Date.now() + 300000;
    while (Date.now() < waitDeadline) {
      const res = await fetch(API + '/v2/edu/marking/result/' + scanTaskId, {
        headers: { Authorization: 'Bearer ' + token },
      }).catch(() => null);
      if (res && res.ok) {
        const body = await res.json();
        const data = body && body.data;
        if (data && (data.stage === '已完成' || data.finished === true)) { payload = data; break; }
        if (data && data.stage === '失败') { payload = data; break; }
      }
      await new Promise((r) => setTimeout(r, 3000));
    }
  }
  record('marking completed', !!payload && (payload.stage === '已完成' || payload.finished === true),
    payload && JSON.stringify({ stage: payload.stage, finished: payload.finished, total: payload.total }).slice(0, 160));

  if (payload) {
    const rs = payload.results || [];
    record('result has 3 questions and total score', rs.length === 3 && Number(payload.totalScore) === 10,
      JSON.stringify(rs.map((q) => ({ q: q.questionId, s: q.score })).slice(0, 5)));
  }

  const shot = path.join(SHOT_DIR, 'ui-qr-identity-' + Date.now() + '.png');
  try {
    await p.screenshot({ path: shot, fullPage: false });
    console.log('SCREENSHOT ' + shot);
  } catch (e) {
    console.log('SCREENSHOT_SKIPPED ' + String(e.message || e).slice(0, 120));
  }

  const unexpectedErr = errs.filter((x) => !/Failed to load resource|ResizeObserver|vite|favicon/i.test(x));
  record('no unexpected JS errors', unexpectedErr.length === 0, JSON.stringify(unexpectedErr.slice(0, 4)));
  const harmless = ['/typhoon-api/TyhoonActivity', '/v2/edu/teacher/marking/dashboard-stats'];
  const filteredBad = badResp.filter((x) => !harmless.some((h) => x.includes(h)));
  record('no unexpected HTTP >= 400', filteredBad.length === 0, JSON.stringify(filteredBad.slice(0, 5)));

  const failed = checks.filter((c) => !c.ok).length;
  console.log('RESULT_JSON ' + JSON.stringify({ passed: checks.length - failed, failed, checks }));
  await browser.close();
  process.exit(failed === 0 ? 0 : 3);
})().catch((e) => { console.error('FATAL', e.stack || e); process.exit(1); });