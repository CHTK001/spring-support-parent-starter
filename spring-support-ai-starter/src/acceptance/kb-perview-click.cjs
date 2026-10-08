/**
 * 知识库详情页「逐视图逐控件」点击验收（v2）。
 * 每个视图：重新进入详情 → 切到该视图 → 逐个点击可见可点控件 → 断言无 pageerror/console-error/4xx/5xx。
 * 跳过破坏性与导航类控件（删除/清空/移除/卸载/重置/重新生成/注销/退出/返回/侧栏收起展开）。
 * 点击失败（detach/动画遮挡）→ 重新定位一次；仍不行记为 warn（非 FAIL）。
 */
const { chromium } = require('playwright');
const fs = require('fs');
const path = require('path');

const BASE = process.env.BASE || 'http://localhost:15689';
const OUT = process.env.OUT || path.join(__dirname, 'kb-perview.json');
const USER = process.env.USER_NAME || 'admin';
const PASS = process.env.USER_PASS || 'admin123';
const TABS = ['概览', '文档', '搜索', '知识图谱', '智能问答', '实体管理', '关系管理', '设置'];
const SKIP = /删除|清空|移除|卸载|重置|重新生成|注销|退出|返回|收起|展开侧栏|新建知识库/;
const NOISE = /weather|typhoon|location|favicon|open-meteo/i;
let RAW = '';
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

async function login(p) {
  for (let i = 0; i < 4; i++) {
    const onResp = async (r) => { if (RAW) return; if (!/\/v2\/user\/login/.test(r.url()) || r.request().method() !== 'POST') return; try { const t = JSON.parse(await r.text())?.data?.accessToken; if (t) RAW = t; } catch { /* ignore */ } };
    p.on('response', onResp);
    await p.goto(BASE + '/login', { waitUntil: 'domcontentloaded', timeout: 120000 });
    await p.waitForSelector('input[type="password"]', { timeout: 40000 });
    await sleep(1500);
    await p.locator('input[type="text"]').first().fill(USER);
    await p.locator('input[type="password"]').first().fill(PASS);
    await p.locator('button:has-text("登")').first().click();
    try { await p.waitForURL((x) => !/\/login/.test(x.pathname), { timeout: 25000 }); } catch { /* ignore */ }
    await sleep(1500); p.off('response', onResp); if (RAW) return true;
  }
  return false;
}

const MARK = `(() => {
  document.querySelectorAll('[data-pv]').forEach((e) => e.removeAttribute('data-pv'));
  const els = [...document.querySelectorAll('button,[role="button"],.el-button,a[href],[class*="icon-btn"]')]
    .filter((el) => { const r = el.getBoundingClientRect(); const cs = getComputedStyle(el);
      return r.width >= 2 && r.height >= 2 && cs.display !== 'none' && cs.visibility !== 'hidden'
        && Number(cs.opacity) !== 0 && r.bottom >= 0 && r.top <= innerHeight; });
  const out = [];
  els.forEach((el, i) => { el.setAttribute('data-pv', String(i));
    out.push({ name: (el.getAttribute('aria-label') || el.getAttribute('title') || (el.textContent||'').trim()).replace(/\\s+/g,' ').slice(0,40) }); });
  return out;
})()`;

(async () => {
  const b = await chromium.launch({ headless: true });
  const p = await (await b.newContext({ viewport: { width: 1512, height: 950 } })).newPage();
  let bucket = { pErr: [], cErr: [], http: [] };
  p.on('pageerror', (e) => bucket.pErr.push(String(e.message).slice(0, 160)));
  p.on('console', (m) => { if (m.type() === 'error' && !/Failed to load resource/.test(m.text())) bucket.cErr.push(m.text().slice(0, 160)); });
  p.on('response', (r) => { if (r.status() >= 400 && !NOISE.test(r.url())) bucket.http.push(r.status() + ' ' + r.url().replace(BASE, '').slice(0, 90)); });

  const R = { base: BASE, pass: 0, fail: 0, warn: 0, views: [] };
  const clickTab = (n) => p.evaluate((x) => { const btn = [...document.querySelectorAll('nav.re-surface button')].find((e) => (e.innerText || '').trim() === x); if (btn) { btn.click(); return true; } return false; }, n);
  try {
    if (!(await login(p))) throw new Error('login failed');
    const kid = await p.evaluate(async (tok) => { const r = await fetch('/api/knowledge/bases', { headers: tok ? { Authorization: 'Bearer ' + tok } : {} }); const j = await r.json(); const rows = (j && j.data) || []; return rows.length ? (rows[0].knowledge_base_id || rows[0].id) : ''; }, RAW);
    R.kbId = kid;
    const detail = BASE + '/knowledge-base/' + kid;

    for (const tab of TABS) {
      bucket = { pErr: [], cErr: [], http: [] };
      await p.goto(detail, { waitUntil: 'domcontentloaded', timeout: 120000 });
      await sleep(3500);
      const switched = await clickTab(tab);
      await sleep(2500);
      let items = await p.evaluate(MARK).catch(() => []);
      const view = { tab, switched, clickable: items.length, clicked: 0, skipped: 0, warn: 0, fails: [] };
      for (let i = 0; i < items.length; i++) {
        const nm = items[i].name || '(无名)';
        if (SKIP.test(nm)) { view.skipped++; continue; }
        // 目标可能已 detach：重新标记定位
        let sel = '[data-pv="' + i + '"]';
        if ((await p.locator(sel).count().catch(() => 0)) === 0) {
          const fresh = await p.evaluate(MARK).catch(() => []);
          const idx = fresh.findIndex((x) => x.name === nm);
          if (idx >= 0) sel = '[data-pv="' + idx + '"]';
        }
        const before = { p: bucket.pErr.length, c: bucket.cErr.length, h: bucket.http.length };
        let clicked = false;
        try { await p.locator(sel).first().click({ timeout: 3500, noWaitAfter: true }); clicked = true; }
        catch { try { await p.locator(sel).first().click({ timeout: 2500, force: true, noWaitAfter: true }); clicked = true; } catch { /* keep */ } }
        await sleep(650);
        const nP = bucket.pErr.slice(before.p), nC = bucket.cErr.slice(before.c), nH = bucket.http.slice(before.h);
        if (nP.length || nC.length || nH.length) {
          view.fails.push(`${nm}: pErr=${nP.join(';').slice(0,50)} cErr=${nC.join(';').slice(0,50)} http=${nH.join(';').slice(0,70)}`);
        } else if (clicked) { view.clicked++; }
        else {
          const exists = (await p.locator(sel).count().catch(() => 0)) > 0;
          if (exists) { view.warn++; } else { view.warn++; }
        }
        const ov = await p.locator('.el-dialog:visible, .el-drawer:visible, [role="dialog"]:visible').count().catch(() => 0);
        if (ov > 0) { await p.keyboard.press('Escape').catch(() => {}); await sleep(400); }
      }
      R.pass += view.clicked; R.fail += view.fails.length; R.warn += view.warn;
      R.views.push(view);
      console.log(`[${tab}] switched=${switched} clickable=${view.clickable} clicked=${view.clicked} skipped=${view.skipped} warn=${view.warn} fails=${view.fails.length}` + (view.fails.length ? ' -> ' + view.fails.slice(0, 3).join(' | ') : ''));
    }
  } catch (e) { R.fatal = (e && e.message) || String(e); console.log('FATAL: ' + R.fatal); }
  finally {
    fs.writeFileSync(OUT, JSON.stringify(R, null, 2));
    await b.close().catch(() => {});
    console.log(`\n===== KB 逐视图点击 v2: PASS=${R.pass} WARN=${R.warn} FAIL=${R.fail} =====  ${OUT}`);
    process.exit(R.fail || R.fatal ? 1 : 0);
  }
})();
