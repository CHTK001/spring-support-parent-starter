/**
 * 阅卷下载试卷验收（生产环境）：
 * admin 登录 -> 阅卷工作台第一条自有卷(SELF)记录 -> 点「下载试卷」
 * 应通过隐藏 PaperPreview 调用 getPaper 并导出整卷 A4 PNG 到本地。
 */
const fs = require('fs');
const path = require('path');
let pw;
try { pw = require('playwright'); } catch (e) { pw = require('D:/ch/project/vue-support-parent-starter/node_modules/playwright'); }

const BASE = process.argv[2] || 'http://192.168.50.198:19093';
const ADMIN = { user: 'admin', pass: 'admin123' };
const SHOT_DIR = path.join(process.env.TEMP || 'D:/ch/project/.tmp', 'opencode', 'marking-paper-download');
fs.mkdirSync(SHOT_DIR, { recursive: true });

const checks = [];
const record = (name, ok, detail) => {
  checks.push({ name, ok: !!ok, detail: String(detail ?? '') });
  console.log(`  ${ok ? 'PASS' : 'FAIL'}  ${name}${ok ? '' : '   [' + detail + ']'}`);
};

async function loginAdmin(p) {
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
}

(async () => {
  const browser = await pw.chromium.launch({ headless: true });
  const ctx = await browser.newContext({ viewport: { width: 1600, height: 1000 }, acceptDownloads: true });
  const p = await ctx.newPage();
  const errs = [];
  const badResp = [];
  const paperReqs = [];
  const downloads = [];
  p.on('pageerror', (e) => errs.push(String(e.message || e).slice(0, 180)));
  p.on('console', (m) => { if (m.type() === 'error') errs.push(String(m.text()).slice(0, 180)); });
  p.on('response', (r) => { if (r.status() >= 400) badResp.push(r.status() + ' ' + r.url().replace(/^https?:\/\/[^/]+/, '')); });
  p.on('request', (r) => { if (r.url().includes('/v2/edu/paper/') && r.method() === 'GET') paperReqs.push(r.url().replace(/^https?:\/\/[^/]+/, '')); });
  p.on('download', (d) => downloads.push(d));

  await loginAdmin(p);
  await p.goto(BASE + '/edu/marking', { waitUntil: 'domcontentloaded', timeout: 60000 });
  await p.waitForSelector('.mw-records .el-table__row', { timeout: 60000 });
  await p.waitForTimeout(3000);

  const selfRow = p.locator('.mw-records .el-table__row').filter({ hasText: '自有卷' }).first();
  const selfCount = await selfRow.count();
  record('admin sees SELF marking rows', selfCount > 0, 'count=' + selfCount);

  let downloadOk = false;
  let saved = '';
  let gotPaper = false;
  if (selfCount > 0) {
    const dl = selfRow.locator('button[aria-label="下载试卷"]').first();
    if (await dl.count()) {
      const waitDl = p.waitForEvent('download', { timeout: 90000 }).catch(() => null);
      await dl.click();
      const dlEv = await waitDl;
      await p.waitForTimeout(1200);
      gotPaper = paperReqs.some((u) => /\/v2\/edu\/paper\/\d+$/.test(u));
      if (dlEv) {
        const file = path.join(SHOT_DIR, dlEv.suggestedFilename() || 'paper-' + Date.now() + '.png');
        await dlEv.saveAs(file);
        saved = file;
        downloadOk = fs.existsSync(file) && fs.statSync(file).size > 0;
      }
    }
  }
  record('download paper calls getPaper detail', gotPaper, JSON.stringify(paperReqs));
  record('download paper triggers A4 PNG download', downloadOk, 'saved=' + saved);

  const unexpectedErr = errs.filter((x) => !/Failed to load resource|ResizeObserver|vite|favicon/i.test(x));
  record('no unexpected JS errors', unexpectedErr.length === 0, JSON.stringify(unexpectedErr.slice(0, 4)));
  const harmless = ['/typhoon-api/TyhoonActivity', '/v2/edu/teacher/marking/dashboard-stats', '/v2/edu/dimension/dropdown'];
  const filteredBad = badResp.filter((x) => !harmless.some((h) => x.includes(h)));
  record('no unexpected HTTP >= 400', filteredBad.length === 0, JSON.stringify(filteredBad.slice(0, 5)));

  const failed = checks.filter((c) => !c.ok).length;
  console.log('RESULT_JSON ' + JSON.stringify({ passed: checks.length - failed, failed, checks }));
  await browser.close();
  process.exit(failed === 0 ? 0 : 3);
})().catch((e) => { console.error('FATAL', e.stack || e); process.exit(1); });