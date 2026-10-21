const fs = require('fs');
let pw; try { pw = require('playwright'); } catch (e) { pw = require('D:/ch/project/vue-support-parent-starter/node_modules/playwright'); }
const BASE = 'http://192.168.50.198:19093';
const OUT = 'D:/ch/project/spring-support-parent-starter/spring-support-eduai-starter/src/acceptance/_wrongbook-acceptance.txt';
const log = (...a) => { const line = a.join(' '); console.log(line); fs.appendFileSync(OUT, line + '\n'); };
const R = [];
const add = (name, ok, detail) => { R.push({ name, ok: !!ok, detail: detail || '' }); log((ok ? '  PASS  ' : '  FAIL  ') + name + (detail ? '   [' + detail + ']' : '')); };

(async () => {
  fs.writeFileSync(OUT, '');
  const b = await pw.chromium.launch({ headless: true });
  const p = await (await b.newContext({ viewport: { width: 1440, height: 900 } })).newPage();
  const bad = [];
  p.on('response', r => { if (r.status() >= 400) bad.push(r.status() + ' ' + r.url().replace(/^https?:\/\/[^/]+/, '').slice(0, 100)); });

  // 教师登录
  await p.goto(BASE + '/edu/student-login', { waitUntil: 'domcontentloaded', timeout: 60000 });
  await p.waitForSelector('.sl-form input[type="text"]', { timeout: 60000 });
  await p.locator('.sl-field__box input[type="text"]').first().fill('ACC-1791086381');
  await p.locator('.sl-field__box input[type="password"]').first().fill('Teach@12345');
  await p.locator('button.sl-submit').first().click();
  await p.waitForTimeout(8000);

  // 错题库页
  await p.goto(BASE + '/edu/wrongbook', { waitUntil: 'domcontentloaded', timeout: 60000 });
  await p.waitForTimeout(6000);
  await p.waitForFunction(() => document.querySelectorAll('.el-table__row').length > 0, null, { timeout: 30000 }).catch(() => {});
  await p.waitForTimeout(1000);

  // 找到种子行（学生 2026000004 + 未订正标签）
  const row = p.locator('.el-table__row', { hasText: '2026000004' }).first();
  const rowCount = await row.count();
  add('错题库列表加载且含目标学生行', rowCount > 0, 'rows=' + rowCount);

  // 行内按钮：溯源 / 订正 / 更新掌握度（按 aria-label）
  const rowButtons = row.locator('button[aria-label]');
  const labels = await rowButtons.evaluateAll(btns => btns.map(x => x.getAttribute('aria-label')));
  log('ROW_BUTTONS', JSON.stringify(labels));
  const hasTrace = labels.includes('溯源');
  const hasCorrect = labels.includes('订正');
  add('行内有「溯源」按钮', hasTrace, JSON.stringify(labels));
  add('行内有「订正」按钮', hasCorrect, JSON.stringify(labels));

  // 点溯源 → 弹窗列出来源任务
  if (hasTrace) {
    await row.locator('button[aria-label="溯源"]').click();
    await p.waitForTimeout(2500);
    const dialog = await p.evaluate(() => {
      const d = Array.from(document.querySelectorAll('.el-dialog')).find(x => x.offsetParent !== null && /来源任务/.test((x.innerText || '')));
      return d ? { has: true, text: (d.innerText || '').replace(/\s+/g, ' ').slice(0, 220) } : { has: false };
    });
    log('TRACE_DIALOG', JSON.stringify(dialog));
    add('溯源弹窗出现并含来源信息', dialog.has && /来源任务/.test(dialog.text || ''), dialog.text || '');
    // 关闭弹窗
    await p.locator('.el-dialog button', { hasText: '关闭' }).first().click().catch(() => {});
    await p.waitForTimeout(800);
  }

  // 点订正 → 该行「未订正」变「已订正」
  if (hasCorrect) {
    const before = await row.locator('.el-tag').allTextContents().catch(() => []);
    await row.locator('button[aria-label="订正"]').click();
    await p.waitForTimeout(2000);
    const after = await p.locator('.el-table__row', { hasText: '2026000004' }).first().innerText().catch(() => '');
    const afterTags = await p.locator('.el-table__row', { hasText: '2026000004' }).first().locator('.el-tag').allTextContents().catch(() => []);
    log('CORRECT_BEFORE_TAGS', JSON.stringify(before));
    log('CORRECT_AFTER_TAGS', JSON.stringify(afterTags));
    add('订正后该行显示「已订正」', afterTags.some(t => (t || '').trim() === '已订正'), JSON.stringify(afterTags));
  }

  const unexpectedBad = bad.filter(x => !/avatar-frame|typhoon/.test(x));
  add('无意外 HTTP>=400', unexpectedBad.length === 0, JSON.stringify(unexpectedBad.slice(0, 3)));
  const failed = R.filter(x => !x.ok).length;
  log('RESULT_JSON ' + JSON.stringify({ failed, checks: R }));
  await b.close();
  process.exit(failed === 0 ? 0 : 3);
})().catch(e => { log('FATAL ' + (e.stack || e)); process.exit(1); });