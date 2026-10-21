const fs = require('fs');
let pw; try { pw = require('playwright'); } catch (e) { pw = require('D:/ch/project/vue-support-parent-starter/node_modules/playwright'); }
const BASE = 'http://192.168.50.198:19093';
const OUT = 'D:/ch/project/spring-support-parent-starter/spring-support-eduai-starter/src/acceptance/_batch-acceptance.txt';
const log = (...a) => { const line = a.join(' '); console.log(line); fs.appendFileSync(OUT, line + '\n'); };
const R = [];
const add = (name, ok, detail) => { R.push({ name, ok: !!ok, detail: detail || '' }); log((ok ? '  PASS  ' : '  FAIL  ') + name + (detail ? '   [' + detail + ']' : '')); };

(async () => {
  fs.writeFileSync(OUT, '');
  const b = await pw.chromium.launch({ headless: true });
  const p = await (await b.newContext({ viewport: { width: 1440, height: 900 } })).newPage();
  const bad = [];
  p.on('response', r => { if (r.status() >= 400) bad.push(r.status() + ' ' + r.url().replace(/^https?:\/\/[^/]+/, '').slice(0, 100)); });

  // 登录教师
  await p.goto(BASE + '/edu/student-login', { waitUntil: 'domcontentloaded', timeout: 60000 });
  await p.waitForSelector('.sl-form input[type="text"]', { timeout: 60000 });
  await p.locator('.sl-field__box input[type="text"]').first().fill('ACC-1791086381');
  await p.locator('.sl-field__box input[type="password"]').first().fill('Teach@12345');
  await p.locator('button.sl-submit').first().click();
  await p.waitForTimeout(8000);
  add('教师登录', (await p.evaluate(() => location.pathname)) === '/edu/teacher', '');

  // 进阅卷工作台
  await p.goto(BASE + '/edu/teacher', { waitUntil: 'domcontentloaded', timeout: 60000 });
  await p.waitForTimeout(6000);
  const tab = p.locator('.el-tabs__item, [role="tab"]', { hasText: '阅卷' }).first();
  const tabCount = await tab.count();
  if (tabCount) {
    await tab.click();
    await p.waitForTimeout(5000);
  }
  const inMarking = await p.evaluate(() => {
    const t = document.body.innerText || '';
    return /AI 阅卷记录|批量完成/.test(t) || !!document.querySelector('.mw-records');
  });
  add('切到阅卷工作台', !!tabCount && inMarking, 'tab=' + tabCount + ' in=' + inMarking);

  // 等阅卷记录行出现（只取 mw-records 内，页面还有学生/组卷等其它表格）
  await p.waitForFunction(() => document.querySelectorAll('.mw-records .el-table__row').length > 0, null, { timeout: 30000 }).catch(() => {});
  await p.waitForTimeout(2000);

  // 读取阅卷记录可见行：任务号 + 老师完成列 + 判分状态
  const rows0 = await p.evaluate(() => {
    const rec = document.querySelector('.mw-records');
    const tb = rec ? rec.querySelector('.el-table') : null;
    if (!tb) return [];
    const heads = Array.from(tb.querySelectorAll('thead th')).map(x => (x.innerText || '').trim());
    const finishIdx = heads.indexOf('完成');
    const statusIdx = heads.indexOf('状态');
    const out = [];
    tb.querySelectorAll('.el-table__row').forEach(row => {
      const cells = Array.from(row.querySelectorAll('td')).map(td => (td.innerText || '').trim());
      const text = (row.innerText || '').replace(/\s+/g, ' ').trim();
      const taskNo = text.match(/\b[a-f0-9]{32}\b/)?.[0] || '';
      out.push({
        taskNo,
        teacherFinish: finishIdx >= 0 && cells.length > finishIdx ? cells[finishIdx] : '',
        statusFailed: statusIdx >= 0 && cells.length > statusIdx ? cells[statusIdx] === '失败' : text.includes('失败'),
      });
    });
    return out;
  });
  log('VISIBLE_ROWS_0', JSON.stringify(rows0.slice(0, 6)));
  const todo = rows0.filter(r => r.taskNo && r.teacherFinish === '待完成' && !r.statusFailed);
  add('列表加载出未完成任务', todo.length > 0, 'todo=' + todo.length);

  // 勾选第一、二个可见可完成的未完成任务（ReTable selection checkbox 每行都有）
  const picks = [];
  let clicked = 0;
  for (let i = 0; i < rows0.length && clicked < 2; i++) {
    if (rows0[i].teacherFinish === '待完成' && !rows0[i].statusFailed && rows0[i].taskNo) {
      const row = p.locator('.mw-records .el-table__row').nth(i);
      await row.locator('.el-checkbox').first().click().catch(() => {});
      picks.push(rows0[i].taskNo);
      clicked++;
    }
  }
  add('勾选两行待完成任务', picks.length === 2, JSON.stringify(picks));

  const countBefore = await p.evaluate(async () => {
    const r = await fetch('/v2/edu/teacher/marking/tasks/page?pageNum=1&pageSize=1&teacherFinished=1', { credentials: 'include' });
    const j = await r.json();
    return j?.data?.total ?? -1;
  });

  // 点批量完成
  await p.locator('button', { hasText: '批量完成' }).click().catch(() => {});
  await p.waitForTimeout(5000);
  const doneState = await p.evaluate(async (picks) => {
    const r = await fetch('/v2/edu/teacher/marking/tasks/page?pageNum=1&pageSize=100&teacherFinished=1', { credentials: 'include' });
    const j = await r.json();
    const ids = new Set((j?.data?.data ?? []).map(x => x.markingTaskTaskId));
    return picks.every(t => ids.has(t));
  }, picks);
  add('批量完成后所选任务进入已完成列表', doneState, 'before=' + countBefore);

  // 点批量撤销（重新勾选已完成的两行）
  const rows1 = await p.evaluate(() => {
    const rec = document.querySelector('.mw-records');
    const tb = rec ? rec.querySelector('.el-table') : null;
    if (!tb) return [];
    const heads = Array.from(tb.querySelectorAll('thead th')).map(x => (x.innerText || '').trim());
    const finishIdx = heads.indexOf('完成');
    const statusIdx = heads.indexOf('状态');
    const out = [];
    tb.querySelectorAll('.el-table__row').forEach(row => {
      const cells = Array.from(row.querySelectorAll('td')).map(td => (td.innerText || '').trim());
      const text = (row.innerText || '').replace(/\s+/g, ' ').trim();
      const taskNo = text.match(/\b[a-f0-9]{32}\b/)?.[0] || '';
      out.push({
        taskNo,
        teacherFinish: finishIdx >= 0 && cells.length > finishIdx ? cells[finishIdx] : '',
        statusFailed: statusIdx >= 0 && cells.length > statusIdx ? cells[statusIdx] === '失败' : text.includes('失败'),
      });
    });
    return out;
  });
  let unClicked = 0;
  for (let i = 0; i < rows1.length && unClicked < 2; i++) {
    if (rows1[i].teacherFinish === '已完成') {
      await p.locator('.mw-records .el-table__row').nth(i).locator('.el-checkbox').first().click().catch(() => {});
      unClicked++;
    }
  }
  await p.locator('button', { hasText: '批量撤销' }).click().catch(() => {});
  await p.waitForTimeout(5000);
  const undoneState = await p.evaluate(async (picks) => {
    const r = await fetch('/v2/edu/teacher/marking/tasks/page?pageNum=1&pageSize=100&teacherFinished=0', { credentials: 'include' });
    const j = await r.json();
    const ids = new Set((j?.data?.data ?? []).map(x => x.markingTaskTaskId));
    return picks.every(t => ids.has(t));
  }, picks);
  add('批量撤销后所选任务回到待完成/未完成列表', undoneState, '');

  const unexpectedBad = bad.filter(x => !/avatar-frame|typhoon/.test(x));
  add('无意外 HTTP>=400', unexpectedBad.length === 0, JSON.stringify(unexpectedBad.slice(0, 3)));
  const failed = R.filter(x => !x.ok).length;
  log('RESULT_JSON ' + JSON.stringify({ failed, checks: R }));
  await b.close();
  process.exit(failed === 0 ? 0 : 3);
})().catch(e => { log('FATAL ' + (e.stack || e)); process.exit(1); });