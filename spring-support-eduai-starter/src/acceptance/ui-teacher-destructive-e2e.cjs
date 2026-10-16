/**
 * 老师端（/edu/teacher）破坏性控件的**受控补测**（对应验收报告 §34）。
 *
 * §21.8 的逐元素盲扫在这条路由上跳过了 23 个破坏性控件（发布/下架/归档/删除/撤回等）。
 * 本节把它们里**有二次确认弹窗**的那些点到「确认弹窗出现 → 取消」，全程不改数据：
 *   - 组卷 tab：行内「归档」→ 归档确认 → 取消
 *   - 我的班级 tab：行内「删除」→ 删除确认 → 取消
 *   - 我布置的试卷 tab：行内「撤回」→ 撤回布置 → 取消
 * 无二次确认、点了就写库的（发布 / 下架 / 切换工号）**不在本脚本点击**，在报告中如实标注原因。
 *
 * 前置：/edu/teacher 可达，教师账号有班级与试卷数据。
 * 用法：node ui-teacher-destructive-e2e.cjs http://<host>:19093
 */
let pw;
try { pw = require('playwright'); }
catch (e) { pw = require('D:/ch/project/vue-support-parent-starter/node_modules/playwright'); }

const BASE = process.argv[2] || 'http://192.168.50.198:19093';
const TEACHER = { account: 'ACC-1791086381', pass: 'Teach@12345' };

const log = (...a) => console.log(...a);

async function loginAsTeacher(p) {
  await p.goto(BASE + '/edu/student-login', { waitUntil: 'domcontentloaded' });
  await p.waitForSelector('.sl-form input[type="text"]', { timeout: 60000 });
  await p.locator('.sl-field__box input[type="text"]').first().fill(TEACHER.account);
  await p.locator('.sl-field__box input[type="password"]').first().fill(TEACHER.pass);
  await p.locator('button.sl-submit').first().click();
  await p.waitForTimeout(5000);
  return p.evaluate(() => location.pathname);
}

/** 当前可见的确认弹窗（ReDialog 渲染为 .el-dialog）状态 */
const dlg = (p) => p.evaluate(() => {
  const d = Array.from(document.querySelectorAll('.el-dialog')).find((x) => x.offsetParent !== null);
  return d ? {
    title: (d.querySelector('.el-dialog__title, .el-dialog__header')?.innerText || '').replace(/\s+/g, ' ').trim(),
    body: (d.querySelector('.rc-body, .el-dialog__body')?.innerText || '').replace(/\s+/g, ' ').trim().slice(0, 80),
    buttons: Array.from(d.querySelectorAll('button')).map((x) => (x.innerText || '').replace(/\s+/g, ' ').trim()).filter(Boolean),
  } : null;
});

/** 点确认弹窗的「取消」，返回取消后是否已关闭 */
async function cancelDialog(p) {
  const cancel = p.locator('.el-dialog').last().locator('button', { hasText: '\u53D6\u6D88' }).first();
  if (await cancel.count()) { await cancel.click(); await p.waitForTimeout(900); }
  return !(await dlg(p));
}

async function clickTab(p, name) {
  await p.locator('.el-tabs__item', { hasText: name }).first().click();
  await p.waitForTimeout(3000);
}

/** 在某 tab 内找第一个可点的行内按钮（按可见文本），滚动进视口后返回坐标 */
const findRowButton = async (p, label) => {
  const found = await p.evaluate((lab) => {
    const cand = Array.from(document.querySelectorAll('.el-table__row button, .el-table__row .el-button, tbody tr button'))
      .filter((e) => { const r = e.getBoundingClientRect(); return r.width > 0 && r.height > 0 && e.offsetParent !== null && (e.innerText || '').replace(/\s+/g, ' ').trim() === lab; });
    if (!cand.length) return null;
    // 只把第一个滚进视口，返回它在滚动后的坐标
    cand[0].scrollIntoView({ block: 'center', inline: 'nearest' });
    return { count: cand.length };
  }, label);
  if (!found) return null;
  await p.waitForTimeout(600);
  const box = await p.evaluate((lab) => {
    const cand = Array.from(document.querySelectorAll('.el-table__row button, .el-table__row .el-button, tbody tr button'))
      .filter((e) => { const r = e.getBoundingClientRect(); return r.width > 0 && r.height > 0 && e.offsetParent !== null && (e.innerText || '').replace(/\s+/g, ' ').trim() === lab; });
    if (!cand.length) return null;
    const r = cand[0].getBoundingClientRect();
    return { x: Math.round(r.x + r.width / 2), y: Math.round(r.y + r.height / 2) };
  }, label);
  return box ? { ...box, count: found.count } : null;
};

async function caseAction(p, tabName, label, expectTitle) {
  log('--- ' + tabName + ' / 「' + label + '」 ---');
  await clickTab(p, tabName);
  const btn = await findRowButton(p, label);
  if (!btn) { log('  行内未找到可点「' + label + '」（可能该状态行不存在）'); return; }
  log('  找到 ' + btn.count + ' 个，点第一个 @' + btn.x + ',' + btn.y);
  await p.mouse.move(btn.x, btn.y);
  await p.waitForTimeout(200);
  await p.mouse.click(btn.x, btn.y);
  await p.waitForTimeout(1400);
  const d = await dlg(p);
  log('  确认弹窗: ' + JSON.stringify(d));
  if (!d) { log('  未出现确认弹窗（该按钮无二次确认或未命中）'); return; }
  if (expectTitle && !(d.title || '').includes(expectTitle)) {
    log('  提示：标题不含「' + expectTitle + '」，仍按取消处理（不点确定）');
  }
  const closed = await cancelDialog(p);
  log('  点「取消」后弹窗已关闭: ' + closed);
  const errs = await p.evaluate(() => Array.from(document.querySelectorAll('.el-message--error')).map((x) => (x.innerText || '').trim()));
  log('  取消后的错误提示: ' + JSON.stringify(errs));
}

(async () => {
  const b = await pw.chromium.launch({ headless: true });
  const ctx = await b.newContext({ viewport: { width: 1440, height: 900 } });
  const p = await ctx.newPage();
  const errs = [];
  const badResp = [];
  p.on('pageerror', (e) => errs.push('PAGEERROR: ' + String(e.message).slice(0, 140)));
  p.on('console', (m) => { if (m.type() === 'error') errs.push('CONSOLE: ' + String(m.text()).slice(0, 140)); });
  p.on('response', (r) => { if (r.status() >= 400) badResp.push(r.status() + ' ' + r.request().method() + ' ' + r.url().replace(/^https?:\/\/[^/]+/, '')); });

  log('login ->', await loginAsTeacher(p));
  await p.goto(BASE + '/edu/teacher', { waitUntil: 'domcontentloaded' });
  await p.waitForSelector('main.el-main', { timeout: 60000 });
  await p.waitForTimeout(4000);

  const tabs = await p.evaluate(() => Array.from(document.querySelectorAll('.el-tabs__item')).map((x) => (x.innerText || '').replace(/\s+/g, ' ').trim()));
  log('tabs:', JSON.stringify(tabs));

  try { await caseAction(p, '\u7EC4\u5377', '\u5F52\u6863', '\u5F52\u6863'); } catch (e) { log('组卷/归档 异常: ' + String(e.message).split('\n')[0]); }
  try { await caseAction(p, '\u6211\u7684\u73ED\u7EA7', '\u5220\u9664', '\u5220\u9664'); } catch (e) { log('班级/删除 异常: ' + String(e.message).split('\n')[0]); }
  try { await caseAction(p, '\u6211\u5E03\u7F6E\u7684\u8BD5\u5377', '\u64A4\u56DE', '\u64A4\u56DE'); } catch (e) { log('布置/撤回 异常: ' + String(e.message).split('\n')[0]); }

  log('errors:', JSON.stringify(errs.slice(0, 10)));
  log('badResp:', JSON.stringify(badResp.slice(0, 15)));
  await b.close();
})().catch((e) => { console.error('FATAL', e.stack || e); process.exit(1); });
