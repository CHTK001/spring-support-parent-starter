/*
 * 验证新任务 77a9e017... ：
 * 1) 左侧锚点标记显示「分数·作答」（客观题如 2·C，填空如 2·老骥伏杨）
 * 2) 显示/隐藏标记开关在「版面解析」与「原始试卷」两种模式都生效
 * 3) 第一页锚点数量、第 3 页 16 题、第 4 页 17 题作答文本出现在标记里
 */
const fs = require('fs');
let pw;
try { pw = require('playwright'); } catch (e) { pw = require('D:/ch/project/vue-support-parent-starter/node_modules/playwright'); }
const UI = process.argv[2] || 'http://127.0.0.1:19093';
const API = process.argv[3] || 'http://192.168.50.198:19095';
const TASK = process.argv[4] || '77a9e01736b64ccda6391bf790dc8f1c';
const STUDENT = { account: '2026000001', pass: 'Student@9043New' };
const SHOT = require('path').join(process.env.TEMP || 'D:/ch/project/.tmp', 'opencode', 'marker-answer-tags.png');

const checks = [];
const record = (n, ok, d) => { checks.push({ name: n, ok: !!ok, detail: String(d || '') }); console.log((ok ? '  PASS  ' : '  FAIL  ') + n + (ok ? '' : '   [' + d + ']')); };

(async () => {
  const browser = await pw.chromium.launch({ headless: true });
  const ctx = await browser.newContext({ viewport: { width: 1600, height: 1000 } });
  const p = await ctx.newPage();
  const errs = [];
  p.on('pageerror', (e) => errs.push(String(e.message || e).slice(0, 160)));
  p.on('console', (m) => { if (m.type() === 'error') errs.push(String(m.text()).slice(0, 160)); });

  await p.goto(UI + '/edu/student-login', { waitUntil: 'domcontentloaded' });
  await p.waitForSelector('.sl-form input[type="password"]', { timeout: 60000 });
  await p.locator('.sl-field__box input[type="text"]').first().fill(STUDENT.account);
  await p.locator('.sl-field__box input[type="password"]').first().fill(STUDENT.pass);
  await p.locator('button.sl-submit').first().click();
  await p.waitForTimeout(5000);
  const dlg = p.locator('.el-dialog').filter({ hasText: '首次登录' }).last();
  if (await dlg.count()) { await dlg.waitFor({ state: 'visible', timeout: 20000 }); await dlg.locator('input[type="password"]').nth(1).fill(STUDENT.pass); await dlg.getByRole('button', { name: '设置我的密码' }).click(); await p.waitForTimeout(2000); }

  await p.goto(UI + '/edu/marking/detail?taskId=' + TASK, { waitUntil: 'domcontentloaded' });
  await p.waitForSelector('.sheet-structure', { timeout: 60000 });
  await p.waitForTimeout(2500);

  const p1 = await p.evaluate(() => {
    const anchors = Array.from(document.querySelectorAll('.ss-anchor'));
    const texts = anchors.map((a) => (a.innerText || '').trim());
    return { count: anchors.length, texts };
  });
  record('page1 anchors show score·answer tags', p1.count >= 5 && p1.texts.every((t) => /^\d+\s*[·x]\s*[A-D对错]/.test(t)), JSON.stringify(p1.texts.slice(0, 6)));

  // 直接用页码按钮点击更可靠：跳到第 3 页
  const toPage = async (n) => {
    for (let guard = 0; guard < 12; guard++) {
      const t = await p.locator('.ss-stage-page').innerText().catch(() => '');
      const m = t.match(/(\d+)\s*\/\s*(\d+)/);
      const cur = m ? parseInt(m[1], 10) : -1;
      if (cur === n) return;
      if (cur < 0) { await p.waitForTimeout(500); continue; }
      if (cur < n) {
        await p.locator('.ss-nav--next').first().click();
      } else {
        await p.locator('.ss-nav--prev').first().click();
      }
      await p.waitForTimeout(700);
    }
  };

  await toPage(3);
  await p.waitForTimeout(800);
  const p3 = await p.evaluate(() => {
    const anchors = Array.from(document.querySelectorAll('.ss-anchor'));
    const texts = anchors.map((a) => (a.innerText || '').trim());
    return { count: anchors.length, texts, any16: texts.some((t) => t.includes('山山唯落晖')) };
  });
  record('page3 anchors shown', p3.count >= 6, 'count=' + p3.count);
  record('Q16 anchor shows answer text 山山唯落晖', p3.any16, JSON.stringify(p3.texts));

  await toPage(4);
  await p.waitForTimeout(800);
  const p4 = await p.evaluate(() => {
    const anchors = Array.from(document.querySelectorAll('.ss-anchor'));
    const texts = anchors.map((a) => (a.innerText || '').trim());
    return { count: anchors.length, texts, any17: texts.some((t) => t.includes('老骥伏杨')) };
  });
  record('Q17 anchor shows OCR answer 老骥伏杨', p4.any17, JSON.stringify(p4.texts));

  // 显示/隐藏标记切换（保留在第 4 页）
  const sw = p.locator('.ss-stage-actions .el-switch');
  const before = await p.evaluate(() => !!document.querySelector('.ss-overlay') && !!document.querySelector('.ss-overlay').offsetParent);
  await sw.click();
  await p.waitForTimeout(400);
  const hideP4 = await p.evaluate(() => { const o = document.querySelector('.ss-overlay'); return o ? o.offsetParent === null : true; });
  await sw.click();
  await p.waitForTimeout(400);
  const showP4 = await p.evaluate(() => !!document.querySelector('.ss-overlay') && !!document.querySelector('.ss-overlay').offsetParent);
  record('markers toggle hide/show on 版面解析', before && hideP4 && showP4, JSON.stringify({ before, hideP4, showP4 }));

  // 原始试卷模式同样支持开关
  await p.locator('.ss-mode .el-radio-button').filter({ hasText: '原始试卷' }).click();
  await p.waitForTimeout(600);
  const origShow = await p.evaluate(() => !!document.querySelector('.ss-overlay') && !!document.querySelector('.ss-overlay').offsetParent);
  await sw.click();
  await p.waitForTimeout(400);
  const origHide = await p.evaluate(() => { const o = document.querySelector('.ss-overlay'); return o ? o.offsetParent === null : true; });
  await sw.click();
  await p.waitForTimeout(400);
  record('markers toggle works in 原始试卷 mode', origShow && origHide, JSON.stringify({ origShow, origHide }));

  await p.screenshot({ path: SHOT });
  console.log('SCREENSHOT ' + SHOT);

  const unexpected = errs.filter((x) => !/Failed to load resource|ResizeObserver|vite/.test(x));
  record('no unexpected JS errors', unexpected.length === 0, JSON.stringify(unexpected.slice(0, 4)));

  const failed = checks.filter((c) => !c.ok).length;
  console.log('RESULT_JSON ' + JSON.stringify({ passed: checks.length - failed, failed, checks }));
  await browser.close();
  process.exit(failed === 0 ? 0 : 3);
})().catch((e) => { console.error('FATAL', e.stack || e); process.exit(1); });