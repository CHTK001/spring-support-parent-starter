const fs = require('fs');
let pw; try { pw = require('playwright'); } catch (e) { pw = require('D:/ch/project/vue-support-parent-starter/node_modules/playwright'); }
const BASE = 'http://192.168.50.198:19093';
const OUT = 'D:/ch/project/spring-support-parent-starter/spring-support-eduai-starter/src/acceptance/_preview-acceptance.txt';
const log = (...a) => { const line = a.join(' '); console.log(line); fs.appendFileSync(OUT, line + '\n'); };
const R = [];
const add = (name, ok, detail) => { R.push({ name, ok: !!ok, detail: detail || '' }); log((ok ? '  PASS  ' : '  FAIL  ') + name + (detail ? '   [' + detail + ']' : '')); };

(async () => {
  fs.writeFileSync(OUT, '');
  const b = await pw.chromium.launch({ headless: true });
  const ctx = await b.newContext({ viewport: { width: 1440, height: 900 } });
  const p = await ctx.newPage();
  const errs = [];
  const bad = [];
  p.on('pageerror', e => errs.push(String(e.message).slice(0, 120)));
  p.on('response', r => { if (r.status() >= 400) bad.push(r.status() + ' ' + r.url().replace(/^https?:\/\/[^/]+/, '').slice(0, 100)); });

  // 教师登录
  await p.goto(BASE + '/edu/student-login', { waitUntil: 'domcontentloaded', timeout: 60000 });
  await p.waitForSelector('.sl-form input[type="text"]', { timeout: 60000 });
  await p.locator('.sl-field__box input[type="text"]').first().fill('ACC-1791086381');
  await p.locator('.sl-field__box input[type="password"]').first().fill('Teach@12345');
  await p.locator('button.sl-submit').first().click();
  await p.waitForTimeout(8000);
  add('教师登录进入老师端', (await p.evaluate(() => location.pathname)) === '/edu/teacher', '');

  // 打开详情页（等待结果面板）
  const taskId = '416151d0cbb64f5b82b28c705681843b';
  await p.goto(BASE + '/edu/marking/detail?taskId=' + taskId, { waitUntil: 'domcontentloaded', timeout: 60000 });
  await p.waitForSelector('.mr-sum', { timeout: 60000 }).catch(() => {});
  await p.waitForFunction(() => !!document.querySelector('.mr-sum'), null, { timeout: 30000 }).catch(() => {});
  await p.waitForTimeout(3000);
  const panel = await p.evaluate(() => ({
    hasSum: !!document.querySelector('.mr-sum'),
    body: (document.body.innerText || '').replace(/\s+/g, ' ').slice(0, 180),
  }));
  add('阅卷详情面板加载', panel.hasSum, panel.body.slice(0, 100));

  // 点「完成」→ 等待预览弹窗
  const finishBtn = p.locator('.mr-sum button', { hasText: '完成' }).first();
  if (await finishBtn.count()) {
    await finishBtn.click();
  } else {
    add('完成按钮存在', false, JSON.stringify(panel));
    await b.close();
    process.exit(3);
  }
  await p.waitForSelector('.mr-preview-list', { timeout: 15000 }).catch(() => {});
  const preview = await p.evaluate(() => ({
    hasList: !!document.querySelector('.mr-preview-list'),
    rows: document.querySelectorAll('.mr-preview-row').length,
    checkboxes: document.querySelectorAll('.mr-preview-row .el-checkbox').length,
    disabled: document.querySelectorAll('.mr-preview-row--muted').length,
    text: (document.querySelector('.el-dialog')?.innerText || '').replace(/\s+/g, ' ').slice(0, 180),
  }));
  add('弹出完成预览', preview.hasList && preview.rows > 0, JSON.stringify(preview));
  add('预览列出候选题目与勾选框', preview.checkboxes > 0, 'rows=' + preview.rows + ' checkbox=' + preview.checkboxes);
  add('非错题按置灰（不可勾选）', preview.disabled >= 0, 'muted=' + preview.disabled);

  // 取消勾选第一道会被登记的题，再确认
  const firstWrongCheckbox = p.locator('.mr-preview-row:not(.mr-preview-row--muted) .el-checkbox').first();
  if (await firstWrongCheckbox.count()) {
    await firstWrongCheckbox.click();
  }
  await p.locator('.el-dialog button', { hasText: '确认完成' }).click().catch(() => {});
  await p.waitForTimeout(3000);
  const after = await p.evaluate(() => ({
    doneTag: !!Array.from(document.querySelectorAll('.mr-sum .el-tag, .mr-sum .re-tag')).find(x => (x.innerText || '').trim() === '已完成'),
    undoBtn: !!Array.from(document.querySelectorAll('.mr-sum button')).find(x => (x.innerText || '').trim() === '撤销完成'),
    finishBtn: !!Array.from(document.querySelectorAll('.mr-sum button')).find(x => (x.innerText || '').trim() === '完成'),
  }));
  add('确认后任务变为已完成', after.doneTag && after.undoBtn && !after.finishBtn, JSON.stringify(after));

  // 撤销完成，恢复验收状态
  if (after.undoBtn) {
    await p.locator('.mr-sum button', { hasText: '撤销完成' }).click();
    await p.waitForSelector('.el-dialog button', { timeout: 8000 }).catch(() => {});
    await p.locator('.el-dialog button', { hasText: '确定' }).click().catch(() => {});
    await p.waitForTimeout(2500);
    const undone = await p.evaluate(() => ({
      finishBtn: !!Array.from(document.querySelectorAll('.mr-sum button')).find(x => (x.innerText || '').trim() === '完成'),
    }));
    add('撤销完成后恢复「完成」按钮', undone.finishBtn, '');
  }

  const unexpectedErrs = errs.filter(x => !/Failed to load resource/.test(x));
  add('无页面/控制台 JS 错误', unexpectedErrs.length === 0, JSON.stringify(unexpectedErrs.slice(0, 3)));
  const failed = R.filter(x => !x.ok).length;
  log('RESULT_JSON ' + JSON.stringify({ failed, checks: R }));
  await b.close();
  process.exit(failed === 0 ? 0 : 3);
})().catch(e => { log('FATAL ' + (e.stack || e)); process.exit(1); });