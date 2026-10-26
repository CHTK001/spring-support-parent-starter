/**
 * 端到端：导出 9043「已作答卷」手写版 A4 PNG，并投递到阅卷/扫码判分（ANY 无 paperId，靠整卷二维码归回 9043）。
 */
const fs = require('fs');
const path = require('path');
let pw;
try { pw = require('playwright'); } catch (e) { pw = require('D:/ch/project/vue-support-parent-starter/node_modules/playwright'); }

const BASE = process.argv[2] || 'http://192.168.50.198:19093';
const API = process.argv[3] || 'http://192.168.50.198:19095';
const PAPER_ID = process.argv[4] || '9043';
const TEACHER = { account: 'ACC-1791086381', pass: 'Teach@12345' };
const SHOT_DIR = path.join(process.env.TEMP || 'D:/ch/project/.tmp', 'opencode', 'paper-9043-e2e');
fs.mkdirSync(SHOT_DIR, { recursive: true });

const checks = [];
const record = (name, ok, detail) => {
  checks.push({ name, ok: !!ok, detail: String(detail ?? '') });
  console.log(`  ${ok ? 'PASS' : 'FAIL'}  ${name}${ok ? '' : '   [' + detail + ']'}`);
};

(async () => {
  const browser = await pw.chromium.launch({ headless: true });
  const ctx = await browser.newContext({ viewport: { width: 1600, height: 1000 }, acceptDownloads: true });
  const p = await ctx.newPage();
  const errs = [];
  const badResp = [];
  const downloaded = [];

  p.on('pageerror', (e) => errs.push(String(e.message || e).slice(0, 160)));
  p.on('console', (m) => { if (m.type() === 'error') errs.push(String(m.text()).slice(0, 160)); });
  p.on('response', (r) => { if (r.status() >= 400) badResp.push(r.status() + ' ' + r.url().replace(/^https?:\/\/[^/]+/, '')); });
  p.on('download', async (d) => {
    const name = d.suggestedFilename() || `page-${downloaded.length + 1}.png`;
    const idx = /page-(\d+)\.png$/i.exec(name)?.[1] || String(downloaded.length + 1);
    const target = path.join(SHOT_DIR, `page-${idx}.png`);
    await d.saveAs(target);
    downloaded.push(target);
  });

  // 登录老师
  await p.goto(BASE + '/edu/student-login', { waitUntil: 'domcontentloaded', timeout: 60000 });
  await p.waitForSelector('.sl-field__box input[type="text"]', { timeout: 60000 });
  await p.locator('.sl-field__box input[type="text"]').first().fill(TEACHER.account);
  await p.locator('.sl-field__box input[type="password"]').first().fill(TEACHER.pass);
  await p.locator('button.sl-submit').first().click();
  await p.waitForTimeout(5000);

  // 打开组卷预览
  await p.goto(BASE + `/eduai/paper-compose?paperId=${PAPER_ID}`, { waitUntil: 'domcontentloaded' });
  await p.waitForSelector('.compose-list, .pp-page', { timeout: 60000 }).catch(() => {});
  await p.waitForTimeout(6000);

  // 打开导出弹框，选「已作答卷」
  const candidates = p.locator('button[aria-label="导出 A4 图片"]');
  record('export button available', (await candidates.count()) > 0, String(await candidates.count()));
  if (await candidates.count()) {
    await candidates.first().click();
    await p.waitForTimeout(1500);
    const dlg = p.locator('.paper-export-dialog').last();
    if (await dlg.count()) {
      const answered = dlg.locator('.el-radio-button').filter({ hasText: '已作答卷' }).first();
      if (await answered.count()) {
        await answered.click();
        await p.waitForTimeout(500);
        const startBtn = dlg.locator('button').filter({ hasText: /导出 \d+ 页图片/ }).last();
        if (await startBtn.count()) {
          await startBtn.click();
          // 等待多页下载完成（最多 120s）
          const deadline = Date.now() + 120000;
          while (downloaded.length === 0 && Date.now() < deadline) await p.waitForTimeout(1000);
          await p.waitForTimeout(3000);
        }
      }
    }
  }

  record('answered paper downloaded to local', downloaded.length >= 1, `files=${downloaded.length} dir=${SHOT_DIR}`);

  // 确保所有页面下载完成：下载计数 3 秒内不再增长即视为完成（最多 90s）
  let lastCount = downloaded.length;
  let settleTick = 0;
  const settleDeadline = Date.now() + 90000;
  while (Date.now() < settleDeadline && downloaded.length < 12) {
    await p.waitForTimeout(1000);
    if (downloaded.length !== lastCount) {
      lastCount = downloaded.length;
      settleTick = 0;
    } else {
      settleTick += 1;
      if (settleTick >= 3) break;
    }
  }
  await p.waitForTimeout(2000);

  // API 登录并投递阅卷（ANY 无 paperId，二维码自动归回 9043）
  let taskId = '';
  let scanMsg = '';
  if (downloaded.length > 0) {
    const login = await fetch(API + '/v2/user/login', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ username: 'admin', password: 'admin123' }),
    });
    const loginBody = await login.json();
    const token = (loginBody.data && loginBody.data.accessToken) || '';

    const form = new FormData();
    for (let i = 0; i < downloaded.length; i++) {
      form.append('file', new Blob([fs.readFileSync(downloaded[i])], { type: 'image/png' }), `page-${i + 1}.png`);
    }
    form.append('mode', 'ANY');
    form.append('studentId', '20260001');

    const scanRes = await fetch(API + '/v2/edu/marking/scan', {
      method: 'POST',
      headers: { Authorization: 'Bearer ' + token },
      body: form,
    });
    const scanText = await scanRes.text();
    try {
      const scanBody = JSON.parse(scanText);
      taskId = String(scanBody.data || '');
      scanMsg = scanBody.msg || '';
    } catch { scanMsg = scanText.slice(0, 200); }
  }
  record('scan returned taskId', !!taskId, `task=${taskId} msg=${scanMsg}`);

  // 轮询判分完成
  let result = null;
  if (taskId) {
    const tokenLogin = await fetch(API + '/v2/user/login', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ username: 'admin', password: 'admin123' }),
    });
    const loginBody = await tokenLogin.json();
    const token = (loginBody.data && loginBody.data.accessToken) || '';
    const deadline = Date.now() + 300000;
    while (Date.now() < deadline) {
      const res = await fetch(API + `/v2/edu/marking/result/${taskId}`, {
        headers: { Authorization: 'Bearer ' + token },
      }).catch(() => null);
      if (res && res.ok) {
        const body = await res.json();
        const data = body && body.data;
        if (data && (data.stage === '已完成' || data.finished === true)) { result = data; break; }
        if (data && data.stage === '失败') { result = data; break; }
      }
      await new Promise((r) => setTimeout(r, 3000));
    }
  }
  record('marking finished', !!result && (result?.stage === '已完成' || result?.finished === true),
    result && JSON.stringify({ stage: result.stage, total: result.total, totalScore: result.totalScore }));
  if (result) {
    const rs = result.results || [];
    record('has parsed results', rs.length > 0, 'count=' + rs.length);
    console.log('SAMPLE ' + JSON.stringify(rs.slice(0, 5).map((q) => ({ q: q.questionId, s: q.score, max: q.maxScore }))));
  }

  const shot = path.join(SHOT_DIR, 'final-' + Date.now() + '.png');
  await p.screenshot({ path: shot, fullPage: false }).catch(() => {});
  console.log('SCREENSHOT ' + shot);

  const unexpectedErr = errs.filter((x) => !/Failed to load resource|ResizeObserver|vite|favicon/i.test(x));
  record('no unexpected JS errors', unexpectedErr.length === 0, JSON.stringify(unexpectedErr.slice(0, 4)));
  const harmless = ['/typhoon-api/TyhoonActivity', '/v2/edu/teacher/marking/dashboard-stats', '/v2/edu/dimension/dropdown'];
  record('no unexpected HTTP >= 400', badResp.filter((x) => !harmless.some((h) => x.includes(h))).length === 0,
    JSON.stringify(badResp.filter((x) => !harmless.some((h) => x.includes(h))).slice(0, 5)));

  const failed = checks.filter((c) => !c.ok).length;
  console.log('RESULT_JSON ' + JSON.stringify({ passed: checks.length - failed, failed, checks }));
  await browser.close();
  process.exit(failed === 0 ? 0 : 3);
})().catch((e) => { console.error('FATAL', e.stack || e); process.exit(1); });