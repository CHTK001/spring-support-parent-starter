/**
 * 老师端「发布 / 下架 / 切换工号」的**往返受控测**（对应验收报告 §35）。
 *
 * 这三个控件在 §21.8 盲扫与 §34 补测里都没被真实点击过，原因：
 *   - 发布 / 下架：无二次确认，点击即写库；
 *   - 切换工号：纯前端 state（一点整页换成绑定表单），会让后续元素失效。
 *
 * 本脚本用**可逆**路径把它们测成实测，并保证数据/前端状态回到原样：
 *   1) 组卷 tab：找一张「草稿(status=0)」的行 → 点「发布」→ 断言该行变「下架」且状态=已发布
 *                → 再点「下架」→ 断言该行回到「发布」且状态=草稿（**往返还原**）
 *   2) 顶部「切换工号」→ 断言出现绑定表单（.tp-bind / 工号输入）→ 重新加载页面还原
 *
 * 用法：node ui-teacher-publish-roundtrip-e2e.cjs http://<host>:19093
 *
 * 注意：本脚本会真实写库（发布一张草稿卷再下架还原）。若中途中断，
 * 可能有一张卷停留在「已发布」。运行后请核对草稿/已发布条数是否回到运行前。
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

async function gotoComposeTab(p) {
  await p.goto(BASE + '/edu/teacher', { waitUntil: 'domcontentloaded' });
  await p.waitForSelector('main.el-main', { timeout: 60000 });
  await p.waitForTimeout(3500);
  await p.locator('.el-tabs__item', { hasText: '\u7EC4\u5377' }).first().click();
  await p.waitForTimeout(4000);
}

/** 翻到第 n 页（ReTable 自定义 pager：.rm-pager__btn 里的页码按钮） */
async function gotoPage(p, n) {
  const ok = await p.evaluate((num) => {
    const btns = Array.from(document.querySelectorAll('.rm-pager__btn'));
    const t = btns.find((b) => (b.innerText || '').trim() === String(num));
    if (!t) return false;
    t.scrollIntoView({ block: 'center' });
    return true;
  }, n);
  if (!ok) return false;
  await p.waitForTimeout(400);
  await p.evaluate((num) => {
    const btns = Array.from(document.querySelectorAll('.rm-pager__btn'));
    const t = btns.find((b) => (b.innerText || '').trim() === String(num));
    if (t) t.click();
  }, n);
  await p.waitForTimeout(2500);
  return true;
}

/** 在组卷表里翻页找带某按钮的行（最多翻到 maxPage） */
async function findRowWithButtonPaged(p, label, maxPage) {
  for (let pg = 1; pg <= maxPage; pg++) {
    const hit = await findRowWithButton(p, label);
    if (hit) return hit;
    if (pg < maxPage) { const ok = await gotoPage(p, pg + 1); if (!ok) break; }
  }
  return null;
}

/**
 * 找第一行含指定按钮文本的试卷行；返回 { name, x, y }（先滚进视口）。
 */
async function findRowWithButton(p, label) {
  const info = await p.evaluate((lab) => {
    const rows = Array.from(document.querySelectorAll('.el-table__row, tbody tr')).filter((r) => r.offsetParent !== null);
    for (const r of rows) {
      const btn = Array.from(r.querySelectorAll('button, .el-button')).find((b) => (b.innerText || '').replace(/\s+/g, ' ').trim() === lab);
      if (!btn) continue;
      // 取该行的试卷名（第二/三列文本里最长的一段作标识）
      const texts = Array.from(r.querySelectorAll('td')).map((td) => (td.innerText || '').replace(/\s+/g, ' ').trim()).filter(Boolean);
      const name = texts.find((t) => /[\u4e00-\u9fa5A-Za-z0-9]/.test(t) && t.length >= 4 && !/^(草稿|已发布|已归档|组卷|发布|下架|归档|布置给班级)$/.test(t)) || texts.join('|');
      btn.scrollIntoView({ block: 'center', inline: 'nearest' });
      return { name: name.slice(0, 60), texts: texts.slice(0, 8) };
    }
    return null;
  }, label);
  if (!info) return null;
  await p.waitForTimeout(600);
  const box = await p.evaluate((lab) => {
    const rows = Array.from(document.querySelectorAll('.el-table__row, tbody tr')).filter((r) => r.offsetParent !== null);
    for (const r of rows) {
      const btn = Array.from(r.querySelectorAll('button, .el-button')).find((b) => (b.innerText || '').replace(/\s+/g, ' ').trim() === lab);
      if (!btn) continue;
      const rr = btn.getBoundingClientRect();
      return { x: Math.round(rr.x + rr.width / 2), y: Math.round(rr.y + rr.height / 2) };
    }
    return null;
  }, label);
  return box ? { ...info, ...box } : null;
}

/** 在「包含某试卷名」的行里点指定按钮；翻页查找，绝不点错行 */
async function clickInNamedRow(p, name, label, maxPage) {
  for (let pg = 1; pg <= maxPage; pg++) {
    const box = await p.evaluate(({ nm, lab }) => {
      const rows = Array.from(document.querySelectorAll('.el-table__row, tbody tr')).filter((r) => r.offsetParent !== null);
      const r = rows.find((x) => (x.innerText || '').includes(nm));
      if (!r) return null;
      const btn = Array.from(r.querySelectorAll('button, .el-button')).find((b) => (b.innerText || '').replace(/\s+/g, ' ').trim() === lab);
      if (!btn) return null;
      btn.scrollIntoView({ block: 'center', inline: 'nearest' });
      const rr = btn.getBoundingClientRect();
      return { x: Math.round(rr.x + rr.width / 2), y: Math.round(rr.y + rr.height / 2) };
    }, { nm: name, lab: label });
    if (box) {
      await p.waitForTimeout(400);
      const box2 = await p.evaluate(({ nm, lab }) => {
        const rows = Array.from(document.querySelectorAll('.el-table__row, tbody tr')).filter((r) => r.offsetParent !== null);
        const r = rows.find((x) => (x.innerText || '').includes(nm));
        if (!r) return null;
        const btn = Array.from(r.querySelectorAll('button, .el-button')).find((b) => (b.innerText || '').replace(/\s+/g, ' ').trim() === lab);
        if (!btn) return null;
        const rr = btn.getBoundingClientRect();
        return { x: Math.round(rr.x + rr.width / 2), y: Math.round(rr.y + rr.height / 2) };
      }, { nm: name, lab: label });
      if (!box2) return false;
      await p.mouse.move(box2.x, box2.y);
      await p.waitForTimeout(150);
      await p.mouse.click(box2.x, box2.y);
      return true;
    }
    if (pg < maxPage) { const ok = await gotoPage(p, pg + 1); if (!ok) break; }
  }
  return false;
}

/** 该试卷名所在行是否存在指定按钮（翻页查找） */
async function namedRowHasButton(p, name, label, maxPage) {
  for (let pg = 1; pg <= maxPage; pg++) {
    const has = await p.evaluate(({ nm, lab }) => {
      const rows = Array.from(document.querySelectorAll('.el-table__row, tbody tr')).filter((r) => r.offsetParent !== null);
      const r = rows.find((x) => (x.innerText || '').includes(nm));
      if (!r) return false;
      return Array.from(r.querySelectorAll('button, .el-button')).some((b) => (b.innerText || '').replace(/\s+/g, ' ').trim() === lab);
    }, { nm: name, lab: label });
    if (has) return true;
    if (pg < maxPage) { const ok = await gotoPage(p, pg + 1); if (!ok) break; }
  }
  return false;
}
async function rowStatusByName(p, name) {
  return p.evaluate((nm) => {
    const rows = Array.from(document.querySelectorAll('.el-table__row, tbody tr')).filter((r) => r.offsetParent !== null);
    const r = rows.find((x) => (x.innerText || '').includes(nm));
    if (!r) return null;
    const btns = Array.from(r.querySelectorAll('button, .el-button')).map((b) => (b.innerText || '').replace(/\s+/g, ' ').trim()).filter(Boolean);
    const tags = Array.from(r.querySelectorAll('.el-tag, .re-tag')).map((t) => (t.innerText || '').trim()).filter(Boolean);
    return { buttons: btns, tags };
  }, name);
}

const msgs = (p) => p.evaluate(() => Array.from(document.querySelectorAll('.el-message, .el-notification')).map((x) => (x.innerText || '').replace(/\s+/g, ' ').trim()).filter(Boolean));

(async () => {
  const b = await pw.chromium.launch({ headless: true });
  const ctx = await b.newContext({ viewport: { width: 1440, height: 900 } });
  const p = await ctx.newPage();
  const errs = [];
  const badResp = [];
  p.on('pageerror', (e) => errs.push('PAGEERROR: ' + String(e.message).slice(0, 140)));
  p.on('console', (m) => { if (m.type() === 'error') errs.push('CONSOLE: ' + String(m.text()).slice(0, 140)); });
  p.on('response', async (r) => {
    if (r.status() >= 400) {
      let body = '';
      try { body = (await r.text()).slice(0, 400); } catch (e) { body = 'n/a'; }
      badResp.push({
        s: r.status(), m: r.request().method(),
        u: r.url().replace(/^https?:\/\/[^/]+/, ''),
        auth: (r.request().headers()['authorization'] || '(none)').slice(0, 20),
        body,
      });
    }
  });

  log('login ->', await loginAsTeacher(p));
  await gotoComposeTab(p);

  // ---- 1) 发布 -> 下架 往返 ----
  log('--- 发布 / 下架 往返 ---');
  const draft = await findRowWithButtonPaged(p, '\u53D1\u5E03', 4);
  if (!draft) {
    log('  未找到带「发布」按钮的草稿行（可能当前页无草稿）');
  } else {
    const name = draft.name;
    log('  选中草稿卷: ' + JSON.stringify(name));
    log('  发布前行状态: ' + JSON.stringify(await rowStatusByName(p, name)));

    // 点「发布」——严格在该试卷名所在行内点，避免翻页后点错行
    const okPub = await clickInNamedRow(p, name, '\u53D1\u5E03', 4);
    log('  已在目标行点「发布」: ' + okPub);
    await p.waitForTimeout(2500);
    log('  点「发布」后提示: ' + JSON.stringify(await msgs(p)));
    log('  发布后行状态: ' + JSON.stringify(await rowStatusByName(p, name)));

    // 往返还原：同一行点「下架」
    const okUn = await clickInNamedRow(p, name, '\u4E0B\u67B6', 4);
    log('  已在目标行点「下架」: ' + okUn);
    await p.waitForTimeout(2500);
    log('  点「下架」后提示: ' + JSON.stringify(await msgs(p)));
    log('  下架后行状态: ' + JSON.stringify(await rowStatusByName(p, name)));

    // 还原校验
    const restored = await namedRowHasButton(p, name, '\u53D1\u5E03', 4);
    log('  ★ 还原为草稿（该行重新出现「发布」按钮）: ' + restored);
  }

  // ---- 2) 切换工号 -> 绑定表单 -> 重载还原 ----
  log('--- 切换工号 ---');
  const sw = p.locator('button', { hasText: '\u5207\u6362\u5DE5\u53F7' }).first();
  if (await sw.count()) {
    const before = await p.evaluate(() => ({ bind: !!document.querySelector('.tp-bind'), tabs: !!document.querySelector('.el-tabs') }));
    await sw.click();
    await p.waitForTimeout(1500);
    const after = await p.evaluate(() => ({
      bind: !!document.querySelector('.tp-bind'),
      bindInputs: Array.from(document.querySelectorAll('.tp-bind input')).map((i) => i.placeholder || i.type),
      tabsVisible: !!document.querySelector('.el-tabs'),
    }));
    log('  切换前: ' + JSON.stringify(before));
    log('  切换后: ' + JSON.stringify(after));
    // 还原：重新加载页面
    await p.goto(BASE + '/edu/teacher', { waitUntil: 'domcontentloaded' });
    await p.waitForSelector('main.el-main', { timeout: 60000 });
    await p.waitForTimeout(3500);
    const restored = await p.evaluate(() => ({ bind: !!document.querySelector('.tp-bind'), tabs: !!document.querySelector('.el-tabs') }));
    log('  重载后还原: ' + JSON.stringify(restored));
  } else {
    log('  未找到「切换工号」按钮');
  }

  log('errors:', JSON.stringify(errs.slice(0, 10)));
  log('badResp:', JSON.stringify(badResp.slice(0, 15)));
  await b.close();
})().catch((e) => { console.error('FATAL', e.stack || e); process.exit(1); });
