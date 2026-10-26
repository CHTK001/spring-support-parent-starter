/* 生产截图：9043 组卷页 + 阅卷工作台 + 已导出手写卷 */
let pw;
try { pw = require('playwright'); } catch { pw = require('D:/ch/project/vue-support-parent-starter/node_modules/playwright'); }
const fs = require('fs');
const path = require('path');

const BASE = process.argv[2] || 'http://192.168.50.198:19093';
const TEACHER = { account: 'ACC-1791086381', pass: 'Teach@12345' };
const OUT = path.join(process.env.TEMP || 'D:/ch/project/.tmp', 'opencode', 'screenshots');
fs.mkdirSync(OUT, { recursive: true });

(async () => {
  const browser = await pw.chromium.launch({ headless: true });
  const ctx = await browser.newContext({ viewport: { width: 1600, height: 1000 }, acceptDownloads: true });
  const p = await ctx.newPage();

  await p.goto(BASE + '/edu/student-login', { waitUntil: 'domcontentloaded', timeout: 60000 });
  await p.waitForSelector('.sl-field__box input[type="text"]', { timeout: 60000 });
  await p.locator('.sl-field__box input[type="text"]').first().fill(TEACHER.account);
  await p.locator('.sl-field__box input[type="password"]').first().fill(TEACHER.pass);
  await p.locator('button.sl-submit').first().click();
  await p.waitForTimeout(5000);

  // 9043 组卷/预览
  await p.goto(BASE + '/eduai/paper-compose?paperId=9043', { waitUntil: 'domcontentloaded', timeout: 60000 });
  await p.waitForSelector('.compose-list, .pp-page, .compose-readonly', { timeout: 60000 }).catch(() => {});
  await p.waitForTimeout(6000);
  const composeShot = path.join(OUT, '9043-compose-' + Date.now() + '.png');
  await p.screenshot({ path: composeShot, fullPage: false });
  console.log('COMPOSE ' + composeShot);

  // 阅卷工作台（下载试卷按钮在行操作里）
  await p.goto(BASE + '/edu/marking', { waitUntil: 'domcontentloaded', timeout: 60000 });
  await p.waitForSelector('.mw-records .el-table__row', { timeout: 60000 });
  await p.waitForTimeout(3000);
  const markingShot = path.join(OUT, 'marking-download-' + Date.now() + '.png');
  await p.screenshot({ path: markingShot, fullPage: false });
  console.log('MARKING ' + markingShot);

  await browser.close();

  // 把已存在的 9043 手写卷导出页列出来
  const paperDir = path.join(process.env.TEMP || 'D:/ch/project/.tmp', 'opencode', 'paper-9043-e2e');
  const pages = fs.existsSync(paperDir)
    ? fs.readdirSync(paperDir).filter((x) => /^page-.*\.png$/.test(x)).sort().slice(0, 3).map((x) => path.join(paperDir, x))
    : [];
  console.log('HANDWRITING_PAGES ' + JSON.stringify(pages));
})().catch((e) => { console.error('FATAL', e.stack || e); process.exit(1); });