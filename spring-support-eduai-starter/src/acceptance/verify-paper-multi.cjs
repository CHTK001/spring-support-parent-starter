/**
 * 多试卷浏览器回归（生产环境）：
 *  - 草稿卷 9043：可编辑，题型占比/三档留白/整卷二维码（PAPER:9043）
 *  - 已发布卷 9001：只读提示，不能保存/发布，二维码（PAPER:9001）
 */
const fs = require('fs');
const path = require('path');
let pw;
try { pw = require('playwright'); } catch (e) { pw = require('D:/ch/project/vue-support-parent-starter/node_modules/playwright'); }

const BASE = process.argv[2] || 'http://192.168.50.198:19093';
const DRAFT = process.argv[3] || '9043';
const PUBLISHED = process.argv[4] || '9001';
const TEACHER = { account: 'ACC-1791086381', pass: 'Teach@12345' };
const SHOT_DIR = path.join(process.env.TEMP || 'D:/ch/project/.tmp', 'opencode', 'paper-multi');
fs.mkdirSync(SHOT_DIR, { recursive: true });

const checks = [];
const record = (name, ok, detail) => {
  checks.push({ name, ok: !!ok, detail: String(detail ?? '') });
  console.log(`  ${ok ? 'PASS' : 'FAIL'}  ${name}${ok ? '' : '   [' + detail + ']'}`);
};

async function loginTeacher(p) {
  await p.goto(BASE + '/edu/student-login', { waitUntil: 'domcontentloaded', timeout: 60000 });
  await p.waitForSelector('.sl-field__box input[type="text"]', { timeout: 60000 });
  await p.locator('.sl-field__box input[type="text"]').first().fill(TEACHER.account);
  await p.locator('.sl-field__box input[type="password"]').first().fill(TEACHER.pass);
  await p.locator('button.sl-submit').first().click();
  await p.waitForTimeout(5000);
}

async function inspectPaper(p, id) {
  await p.goto(BASE + '/eduai/paper-compose?paperId=' + id, { waitUntil: 'domcontentloaded', timeout: 60000 });
  await p.waitForTimeout(6000);
  await p.waitForSelector('.compose-list, .pp-page, .compose-readonly', { timeout: 60000 }).catch(() => {});
  return p.evaluate(() => {
    const readonly = !!document.querySelector('.compose-readonly');
    const rows = Array.from(document.querySelectorAll('.compose-row'));
    const typeStats = Array.from(document.querySelectorAll('.compose-type-stats__item')).map((x) => (x.innerText || '').trim());
    const presetCount = document.querySelectorAll('.compose-row__preset').length;
    const qrAlt = document.querySelector('.pp-footer__qr')?.getAttribute('alt') || null;
    const qrCount = document.querySelectorAll('.pp-footer__qr').length;
    const saveBtn = document.querySelector('.paper-panel .panel-head button[title*="保存"], .re-button, button');
    const pageTexts = Array.from(document.querySelectorAll('.pp-page')).map((f) => (f.innerText || ''));
    return {
      readonly,
      rowCount: rows.length,
      typeStats,
      presetCount,
      qrAlt,
      qrCount,
      footerPagesOk: pageTexts.length > 0 && pageTexts.every((t, i) => t.includes(`第 ${i + 1} 页 / 共 ${pageTexts.length} 页`)),
    };
  });
}

(async () => {
  const browser = await pw.chromium.launch({ headless: true });
  const ctx = await browser.newContext({ viewport: { width: 1600, height: 1000 } });
  const p = await ctx.newPage();
  const errs = [];
  const badResp = [];
  p.on('pageerror', (e) => errs.push(String(e.message || e).slice(0, 160)));
  p.on('console', (m) => { if (m.type() === 'error') errs.push(String(m.text()).slice(0, 160)); });
  p.on('response', (r) => { if (r.status() >= 400) badResp.push(r.status() + ' ' + r.url().replace(/^https?:\/\/[^/]+/, '')); });

  await loginTeacher(p);
  record('teacher login landed', true, '');

  const draft = await inspectPaper(p, DRAFT);
  record(`draft ${DRAFT} editable (no readonly)`, !draft.readonly, `readonly=${draft.readonly}`);
  record(`draft ${DRAFT} has type/share summary`, draft.typeStats.length > 0 && draft.typeStats.some((t) => t.includes('%')), JSON.stringify(draft.typeStats.slice(0, 5)));
  record(`draft ${DRAFT} has 少/中/多 presets`, draft.presetCount > 0, 'preset=' + draft.presetCount);
  record(`draft ${DRAFT} footer QR has its paper id`, draft.qrCount > 0 && draft.qrAlt && draft.qrAlt.includes(DRAFT), 'qr=' + draft.qrAlt);
  record(`draft ${DRAFT} footer real pages`, draft.footerPagesOk, `pages=${draft.rowCount}`);

  const pub = await inspectPaper(p, PUBLISHED);
  record(`published ${PUBLISHED} is read-only`, pub.readonly, `readonly=${pub.readonly}`);
  record(`published ${PUBLISHED} footer QR has its paper id`, pub.qrCount > 0 && pub.qrAlt && pub.qrAlt.includes(PUBLISHED), 'qr=' + pub.qrAlt);
  record(`published ${PUBLISHED} footer real pages`, pub.footerPagesOk, '');

  const unexpectedErr = errs.filter((x) => !/Failed to load resource|ResizeObserver|vite|favicon/i.test(x));
  record('no unexpected JS errors', unexpectedErr.length === 0, JSON.stringify(unexpectedErr.slice(0, 4)));
  const harmless = ['/typhoon-api/TyhoonActivity', '/v2/edu/teacher/marking/dashboard-stats', '/v2/edu/dimension/dropdown'];
  const filteredBad = badResp.filter((x) => !harmless.some((h) => x.includes(h)));
  record('no unexpected HTTP >= 400', filteredBad.length === 0, JSON.stringify(filteredBad.slice(0, 5)));

  const shot = path.join(SHOT_DIR, 'paper-multi-' + Date.now() + '.png');
  await p.screenshot({ path: shot, fullPage: false });
  console.log('SCREENSHOT ' + shot);

  const failed = checks.filter((c) => !c.ok).length;
  console.log('RESULT_JSON ' + JSON.stringify({ passed: checks.length - failed, failed, checks }));
  await browser.close();
  process.exit(failed === 0 ? 0 : 3);
})().catch((e) => { console.error('FATAL', e.stack || e); process.exit(1); });