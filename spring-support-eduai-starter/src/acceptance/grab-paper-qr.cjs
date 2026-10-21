const fs = require('fs');
let pw;
try { pw = require('playwright'); } catch (e) { pw = require('D:/ch/project/vue-support-parent-starter/node_modules/playwright'); }
const BASE = process.argv[2] || 'http://127.0.0.1:19093';
const PAPER_ID = process.argv[3] || '9043';
const OUT = process.argv[4] || ('paper-qr-' + PAPER_ID + '.png');
(async () => {
  const browser = await pw.chromium.launch({ headless: true });
  const p = await (await browser.newContext({ viewport: { width: 1440, height: 900 } })).newPage();
  await p.goto(BASE + '/edu/student-login', { waitUntil: 'domcontentloaded', timeout: 60000 });
  await p.waitForSelector('.sl-field__box input[type="text"]', { timeout: 60000 });
  await p.locator('.sl-field__box input[type="text"]').first().fill('ACC-1791086381');
  await p.locator('.sl-field__box input[type="password"]').first().fill('Teach@12345');
  await p.locator('button.sl-submit').first().click();
  await p.waitForTimeout(6000);
  await p.goto(BASE + '/eduai/paper-compose?paperId=' + PAPER_ID, { waitUntil: 'domcontentloaded', timeout: 60000 });
  await p.waitForSelector('.pp-footer__qr', { timeout: 60000 });
  const src = await p.locator('.pp-footer__qr').first().getAttribute('src');
  if (!src || !src.startsWith('data:image/png;base64,')) {
    console.error('NO_QR_SRC ' + (src || 'null').slice(0, 60));
    process.exit(1);
  }
  const b64 = src.split(',')[1];
  fs.writeFileSync(OUT, Buffer.from(b64, 'base64'));
  console.log('QR_SAVED ' + OUT + ' size=' + fs.statSync(OUT).size);
  await browser.close();
})().catch((e) => { console.error('FATAL', e.stack || e); process.exit(1); });