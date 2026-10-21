const fs = require('fs');
const path = require('path');
let pw;
try { pw = require('playwright'); } catch (e) { pw = require('D:/ch/project/vue-support-parent-starter/node_modules/playwright'); }
const BASE = process.argv[2] || 'http://127.0.0.1:19093';
const PAPER_ID = process.argv[3] || '9001';
const OUT_DIR = path.join(__dirname, 'qr-export-' + PAPER_ID);
fs.mkdirSync(OUT_DIR, { recursive: true });

(async () => {
  const browser = await pw.chromium.launch({ headless: true });
  const ctx = await browser.newContext({ viewport: { width: 1440, height: 900 }, acceptDownloads: true });
  const p = await ctx.newPage();
  const saved = [];
  p.on('download', async (dl) => {
    const fname = dl.suggestedFilename();
    const target = path.join(OUT_DIR, fname.replace(/[\\/:*?"<>|]/g, '-'));
    await dl.saveAs(target);
    saved.push(target);
    console.log('DL ' + fname + ' -> ' + target + ' size=' + fs.statSync(target).size);
  });

  await p.goto(BASE + '/edu/student-login', { waitUntil: 'domcontentloaded', timeout: 60000 });
  await p.waitForSelector('.sl-field__box input[type="text"]', { timeout: 60000 });
  await p.locator('.sl-field__box input[type="text"]').first().fill('ACC-1791086381');
  await p.locator('.sl-field__box input[type="password"]').first().fill('Teach@12345');
  await p.locator('button.sl-submit').first().click();
  await p.waitForTimeout(6000);

  await p.goto(BASE + '/eduai/paper-compose?paperId=' + PAPER_ID, { waitUntil: 'domcontentloaded', timeout: 60000 });
  await p.waitForSelector('.pp-footer__qr', { timeout: 60000 });
  await p.waitForTimeout(2000);

  const exportBtn = p.locator('button[aria-label="导出 A4 图片"]');
  if (!(await exportBtn.count())) { console.error('NO_EXPORT_BTN'); process.exit(1); }
  await exportBtn.first().click();
  await p.waitForTimeout(1200);

  const answeredRadio = p.locator('.paper-export-dialog .el-radio-button, .paper-export-dialog .re-radio-button').filter({ hasText: '已作答卷' });
  if (await answeredRadio.count()) { await answeredRadio.first().click(); await p.waitForTimeout(400); }

  const runBtn = p.locator('.paper-export-dialog .el-dialog__footer button, .paper-export-dialog .re-dialog__footer button').filter({ hasText: '导出' }).first();
  const countBtn = p.locator('.paper-export-dialog button').filter({ hasText: '导出' });
  if (!(await countBtn.count())) { console.error('NO_RUN_BTN'); process.exit(1); }
  await countBtn.first().click();
  await p.waitForTimeout(6000);
  await p.waitForFunction(() => false, null, { timeout: 100 }).catch(() => {});

  console.log('SAVED_COUNT ' + saved.length);
  if (!saved.length) { console.error('NO_DOWNLOADS'); process.exit(1); }
  await browser.close();
  process.exit(0);
})().catch((e) => { console.error('FATAL', e.stack || e); process.exit(1); });