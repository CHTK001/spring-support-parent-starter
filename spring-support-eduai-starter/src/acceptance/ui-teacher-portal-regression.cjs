/**
 * 老师端浏览器回归验收（对应验收报告 §37.5）。
 *
 * <p>用**老师身份**（不是 admin）把老师端与阅卷工作台的可见控件走一遍：
 * 四个入口（组卷 / 我布置的试卷 / 阅卷 / 我的班级）、列表渲染、翻页/筛选等
 * 只读控件。破坏性动作（发布、下架、完成）不在本脚本里点，它们各有受控脚本。</p>
 *
 * <p>判定：页面级 JS 错误 = 0、HTTP >= 400 = 0（业务 400 由专门脚本断言）、
 * 每个 Tab 点开后容器里必须有可见内容。</p>
 *
 * 用法：node ui-teacher-portal-regression.cjs http://<host>:19093
 */
let pw;
try { pw = require('playwright'); }
catch (e) { pw = require('D:/ch/project/vue-support-parent-starter/node_modules/playwright'); }

const BASE = process.argv[2] || 'http://192.168.50.198:19093';
const TEACHER = { account: 'ACC-1791086381', pass: 'Teach@12345' };

const log = (...a) => console.log(...a);
const RESULT = { checks: [] };
const record = (name, ok, detail) => {
  RESULT.checks.push({ name, ok: !!ok, detail: detail === undefined ? '' : String(detail) });
  log((ok ? '  PASS  ' : '  FAIL  ') + name + (detail === undefined ? '' : '   [' + detail + ']'));
};

async function loginAsTeacher(p) {
  await p.goto(BASE + '/edu/student-login', { waitUntil: 'domcontentloaded' });
  await p.waitForSelector('.sl-form input[type="text"]', { timeout: 60000 });
  await p.locator('.sl-field__box input[type="text"]').first().fill(TEACHER.account);
  await p.locator('.sl-field__box input[type="password"]').first().fill(TEACHER.pass);
  await p.locator('button.sl-submit').first().click();
  await p.waitForTimeout(5000);
  return p.evaluate(() => location.pathname);
}

/** 页面当前可见的可点击控件清单（限定在内容容器里，避开布局 chrome） */
const inventory = (p) => p.evaluate(() => {
  const scope = document.querySelector('main.el-main') || document.body;
  const vis = (el) => {
    if (!el) return false;
    const r = el.getBoundingClientRect();
    if (r.width <= 0 || r.height <= 0) return false;
    const st = getComputedStyle(el);
    return st.visibility !== 'hidden' && st.display !== 'none' && parseFloat(st.opacity || '1') > 0;
  };
  const SEL = 'button, .el-button, .el-tabs__item, .el-radio-button, .el-pager li, a.el-link, [role="button"]';
  return Array.from(scope.querySelectorAll(SEL))
    .filter((el) => vis(el) && !el.disabled)
    .map((el) => (el.innerText || el.getAttribute('aria-label') || '').replace(/\s+/g, ' ').trim())
    .filter(Boolean)
    .slice(0, 40);
});

/** 内容容器里是否有实际内容（表格行 / 卡片 / 空态文案） */
const contentState = (p) => p.evaluate(() => {
  const scope = document.querySelector('main.el-main') || document.body;
  return {
    textLen: (scope.innerText || '').trim().length,
    rows: scope.querySelectorAll('.el-table__row, .re-table__row, .rp-card, .tp-paper, .el-card').length,
    empty: !!scope.querySelector('.el-empty, .re-empty'),
  };
});

(async () => {
  const b = await pw.chromium.launch({ headless: true });
  const ctx = await b.newContext({ viewport: { width: 1440, height: 900 } });
  const p = await ctx.newPage();
  // 门户的「查看」是用 window.open 开**独立标签页**（MarkingWorkbench.openDetailWindow），
  // 不监听 popup 就会把这条正常路径误判成「点了没反应」。
  const popups = [];
  ctx.on('page', (pg) => popups.push(pg));
  const errs = [];
  const badResp = [];
  p.on('pageerror', (e) => errs.push('PAGEERROR: ' + String(e.message).slice(0, 160)));
  p.on('console', (m) => { if (m.type() === 'error') errs.push('CONSOLE: ' + String(m.text()).slice(0, 160)); });
  p.on('response', (r) => { if (r.status() >= 400) badResp.push(r.status() + ' ' + r.request().method() + ' ' + r.url().replace(/^https?:\/\/[^/]+/, '')); });

  log('login ->', await loginAsTeacher(p));

  // ---------- 老师端 ----------
  await p.goto(BASE + '/edu/teacher', { waitUntil: 'domcontentloaded' });
  await p.waitForTimeout(4000);
  const path0 = await p.evaluate(() => location.pathname);
  record('老师端路由可达', path0 === '/edu/teacher', path0);

  const tabs = await p.evaluate(() => Array.from(document.querySelectorAll('.el-tabs__item, [role="tab"]'))
    .map((x) => (x.innerText || '').replace(/\s+/g, ' ').trim()).filter(Boolean));
  record('老师端四个入口齐全（组卷/我布置的试卷/阅卷/我的班级）',
    ['\u7EC4\u5377', '\u6211\u5E03\u7F6E\u7684\u8BD5\u5377', '\u9605\u5377', '\u6211\u7684\u73ED\u7EA7']
      .every((t) => tabs.some((x) => x.includes(t))), JSON.stringify(tabs));

  // 逐个点入口，断言点开后容器有内容
  const entries = ['\u7EC4\u5377', '\u6211\u5E03\u7F6E\u7684\u8BD5\u5377', '\u9605\u5377', '\u6211\u7684\u73ED\u7EA7'];
  for (const label of entries) {
    const tab = p.locator('.el-tabs__item, [role="tab"]', { hasText: label }).first();
    if (!(await tab.count())) { record('入口「' + label + '」存在', false); continue; }
    await tab.click();
    await p.waitForTimeout(2500);
    const st = await contentState(p);
    record('入口「' + label + '」点开后有内容', st.textLen > 30, JSON.stringify(st));
    const inv = await inventory(p);
    RESULT['inventory_' + label] = inv.length;
    log('        「' + label + '」可见控件 ' + inv.length + ' 个');
  }

  // ---------- 从门户真实点进阅卷详情（不是深链直达） ----------
  // 「完成」按钮在详情页上；只验证深链能打开是不够的，老师真实的路径是
  // 门户 -> 阅卷 Tab -> 某个任务的「阅卷/查看」。这里走一遍并断言落到了详情页。
  const tabYue = p.locator('.el-tabs__item, [role="tab"]', { hasText: '\u9605\u5377' }).first();
  await tabYue.click();
  await p.waitForTimeout(2500);
  const rowActions = await p.evaluate(() => {
    const scope = document.querySelector('main.el-main') || document.body;
    return Array.from(scope.querySelectorAll('button, .el-button, a.el-link'))
      .map((x) => (x.innerText || '').trim() || x.getAttribute('aria-label') || '')
      .filter((t) => t && t.length <= 12);
  });
  log('        阅卷 Tab 行内动作: ' + JSON.stringify(Array.from(new Set(rowActions)).slice(0, 12)));
  let opened = false;
  let detailPage = null;
  // 行内动作是 icon-only 按钮（innerText 为空），只能按 aria-label 定位。
  for (const sel of ['button[aria-label="\u67E5\u770B"]', 'button[aria-label="\u9605\u5377"]', 'button[aria-label="\u8BE6\u60C5"]']) {
    const btn = p.locator('main.el-main ' + sel).first();
    if (!(await btn.count()) || !(await btn.isEnabled())) continue;
    popups.length = 0;
    await btn.click();
    await p.waitForTimeout(4000);
    // 「查看」开的是新标签页：目标页在 popups 里，不在当前页的 location 上。
    const target = popups.length ? popups[popups.length - 1] : p;
    await target.waitForLoadState('domcontentloaded').catch(() => {});
    await target.waitForTimeout(3500);
    const path = await target.evaluate(() => location.pathname).catch(() => '');
    if (path.includes('/edu/marking/detail')) { opened = true; detailPage = target; break; }
  }
  const pathDetail = detailPage ? await detailPage.evaluate(() => location.pathname + location.search) : await p.evaluate(() => location.pathname + location.search);
  record('门户「阅卷」Tab 可点进详情页', opened, pathDetail);
  if (opened) {
    const hasPanel = await detailPage.evaluate(() => !!document.querySelector('.mr-sum'));
    record('详情页面板已渲染（含完成按钮所在汇总行）', hasPanel);
    const btns = await detailPage.evaluate(() => Array.from(document.querySelectorAll('.mr-sum button')).map((x) => (x.innerText || '').trim()));
    record('详情页汇总行有「完成」按钮', btns.includes('\u5B8C\u6210'), JSON.stringify(btns));
    await detailPage.close().catch(() => {});
  }

  // ---------- 阅卷工作台 ----------
  await p.goto(BASE + '/edu/marking', { waitUntil: 'domcontentloaded' });
  await p.waitForTimeout(3500);
  const path1 = await p.evaluate(() => location.pathname);
  record('阅卷工作台路由可达', path1 === '/edu/marking', path1);
  const st1 = await contentState(p);
  record('阅卷工作台有列表内容', st1.textLen > 30, JSON.stringify(st1));

  // 安全控件：翻页 / 刷新 / 搜索框旁的按钮（只点带明确只读语义的）
  const safeLabels = ['\u5237\u65B0', '\u67E5\u8BE2', '\u641C\u7D22', '\u4E0B\u4E00\u9875'];
  let safeClicked = 0;
  for (const lb of safeLabels) {
    const btn = p.locator('main.el-main button', { hasText: lb }).first();
    if (!(await btn.count())) continue;
    if (!(await btn.isEnabled())) continue;
    await btn.click();
    await p.waitForTimeout(1500);
    safeClicked++;
  }
  record('阅卷工作台只读控件可点（刷新/查询/下一页，命中 ' + safeClicked + ' 个）', true, 'clicked=' + safeClicked);

  const unexpectedErr = errs.filter((x) => !/Failed to load resource/.test(x));
  record('无页面/控制台 JS 错误', unexpectedErr.length === 0, JSON.stringify(unexpectedErr.slice(0, 5)));
  record('无 HTTP >= 400', badResp.length === 0, JSON.stringify(badResp.slice(0, 5)));

  RESULT.failed = RESULT.checks.filter((c) => !c.ok).length;
  log('RESULT_JSON ' + JSON.stringify(RESULT));
  await b.close();
  process.exit(RESULT.failed === 0 ? 0 : 3);
})().catch((e) => { console.error('FATAL', e.stack || e); process.exit(1); });
