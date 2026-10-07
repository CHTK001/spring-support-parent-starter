/**
 * 定向验收：/agents 「工作流配置 → 子Agent管理」全链路
 *
 * 背景：SIGNOFF §4 记录该页签调 GET /api/agents/managed/{id}/sub-agents 恒 405
 * （后端无该 @GetMapping），表格永远空白 —— 真实缺陷。
 * 本次验证工作树中的候选修复（改用已存在的 GET /{agentId} 详情契约）。
 *
 * 判定：
 *  1. 打开「子Agent管理」后，不再出现 GET …/sub-agents（尤其不出现 405）；
 *  2. GET /api/agents/managed/{id} 返回 200 且带 sub_agents 数组；
 *  3. 新增子Agent → 表格出现该行（无报错）；编辑 → 行内容更新；
 *     删除 → 行消失；后端详情随之收敛（清理干净）。
 *
 * 用 BASE=http://localhost:15689（vite，工作树源码）。
 */
const { chromium } = require('playwright');
const fs = require('fs');
const path = require('path');

const BASE = process.env.BASE || 'http://localhost:15689';
const OUT = process.env.OUT || path.join(__dirname, 'agents-subagent-accept.json');
const USER = process.env.USER_NAME || 'admin';
const PASS = process.env.USER_PASS || 'admin123';
const AGENT_NAME = 'fixture-zip';
const SUB_NAME = 'zz-accept-subagent';

const checks = [];
const add = (n, ok, d) => {
  checks.push({ name: n, ok: !!ok, detail: String(d == null ? '' : d).slice(0, 300) });
  console.log((ok ? '  [PASS] ' : '  [FAIL] ') + n + ' :: ' + String(d == null ? '' : d).slice(0, 200));
};
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));
const toasts = (page) => page.locator('.el-message').allInnerTexts().catch(() => []);
/** 按标题精确定位弹窗：config 弹窗内也有「添加子Agent」按钮，用 hasText 会误配 */
const dialogByTitle = (page, title) =>
  page.locator('.el-dialog').filter({ has: page.locator('.el-dialog__title', { hasText: title }) }).first();

async function login(p) {
  await p.goto(BASE + '/login', { waitUntil: 'domcontentloaded', timeout: 120000 });
  await p.waitForSelector('input[type="password"]', { timeout: 60000 });
  await sleep(1500);
  await p.locator('input[type="text"]').first().fill(USER);
  await p.locator('input[type="password"]').first().fill(PASS);
  await p.locator('button:has-text("登")').first().click();
  await p.waitForURL((x) => !/\/login/.test(x.pathname), { timeout: 30000 });
  await sleep(1500);
}

(async () => {
  const browser = await chromium.launch({ headless: true });
  const page = await (await browser.newContext({ viewport: { width: 1512, height: 950 } })).newPage();
  const subReq = [];      // 全部 /sub-agents 请求
  const detailReq = [];   // 全部 GET /api/agents/managed/{id}
  const jsErr = [];
  page.on('response', (r) => {
    const u = r.url().replace(BASE, '');
    if (/\/api\/agents\/managed\/[^/]+\/sub-agents(\/|$|\?)/.test(u)) subReq.push(`${r.request().method()} ${r.status()} ${u}`);
    else if (/\/api\/agents\/managed\/[^/?#]+$/.test(u)) detailReq.push(`${r.request().method()} ${r.status()} ${u}`);
  });
  page.on('pageerror', (e) => jsErr.push('pageerror: ' + e.message));
  page.on('console', (m) => { if (m.type() === 'error' && !/Failed to load resource/.test(m.text())) jsErr.push('console: ' + m.text()); });

  try {
    await login(page);
    add('login', true, USER);

    await page.goto(BASE + '/agents', { waitUntil: 'domcontentloaded', timeout: 120000 });
    await sleep(3000);

    // 找 fixture-zip 行（主 Agent）
    const row = page.locator('tr', { hasText: AGENT_NAME }).first();
    add('agents.row.fixtureZip', (await row.count()) > 0, `rows with ${AGENT_NAME}=${await page.locator('tr', { hasText: AGENT_NAME }).count()}`);
    if ((await row.count()) === 0) throw new Error('fixture-zip 行不存在');

    const cfgBtn = row.locator('button:has-text("工作流配置")').first();
    add('agents.configBtn.visible', (await cfgBtn.count()) > 0, '工作流配置按钮');
    const n0 = detailReq.length; const s0 = subReq.length;
    await cfgBtn.click();
    await sleep(2500);

    // 1) 详情契约被调用且 200
    const gotDetail = detailReq.slice(n0);
    add('agents.detail.called', gotDetail.length > 0 && gotDetail.every((x) => / 200 /.test(x)), gotDetail.join(' | ') || 'no GET detail');
    // 2) 不再有 GET .../sub-agents（或至少没有 405）
    const gotSub = subReq.slice(s0);
    const getSub = gotSub.filter((x) => x.startsWith('GET'));
    add('agents.noGetSubagents405', getSub.every((x) => !/ 405 /.test(x)) && getSub.length === 0,
      `sub-agents 请求=${gotSub.length}：${gotSub.join(' | ') || '(无 GET，符合修复预期)'}`);

    // 3) 子Agent管理页签 + 表格
    const tab = page.locator('.el-dialog').filter({ hasText: '工作流配置' }).first();
    add('agents.configDialog.open', (await tab.count()) > 0, '工作流配置弹窗');
    const subTab = tab.locator('.el-tabs__item', { hasText: '子Agent管理' }).first();
    if ((await subTab.count()) > 0) { await subTab.click(); await sleep(1200); }
    const table = tab.locator('.el-table').first();
    add('agents.subTable.present', (await table.count()) > 0, '子Agent表格存在');

    // 清理守卫：若上一轮遗留同名子Agent，先删掉（幂等重跑）
    const stale = tab.locator('tr', { hasText: SUB_NAME });
    if ((await stale.count()) > 0) {
      await stale.first().locator('button:has-text("删除")').first().click().catch(() => {});
      await sleep(800);
      await page.locator('.el-message-box button:has-text("确定")').first().click().catch(() => {});
      await sleep(2000);
    }

    // 4) 新增子Agent
    await tab.locator('button:has-text("添加子Agent")').first().click();
    await sleep(1200);
    const addDlg = dialogByTitle(page, '添加子Agent');
    add('agents.addDialog.open', (await addDlg.isVisible().catch(() => false)), '添加子Agent 弹窗');
    const nameInput = addDlg.getByPlaceholder('请输入子Agent名称');
    add('agents.addDialog.nameInput', (await nameInput.count()) > 0, `name inputs=${await nameInput.count()}`);
    await nameInput.fill(SUB_NAME);
    await addDlg.getByPlaceholder('如：分析、总结、翻译').fill('acceptance');
    await addDlg.locator('button:has-text("保存")').first().click();
    await sleep(2500);
    const addToast = (await toasts(page)).join(' | ');
    add('agents.addSubAgent.ok', /添加成功|成功/.test(addToast) && !/失败/.test(addToast), `toast="${addToast}"`);

    // 5) 新增后表格出现该行（详情契约刷新）
    await sleep(800);
    const rowAfter = tab.locator('tr', { hasText: SUB_NAME });
    add('agents.subRow.appears', (await rowAfter.count()) > 0, `含 ${SUB_NAME} 的行=${await rowAfter.count()}`);

    // 6) 编辑
    let edited = false;
    if ((await rowAfter.count()) > 0) {
      await rowAfter.first().locator('button:has-text("编辑")').first().click();
      await sleep(1200);
      const editDlg = dialogByTitle(page, '编辑子Agent');
      if (await editDlg.isVisible().catch(() => false)) {
        await editDlg.getByPlaceholder('如：分析、总结、翻译').fill('acceptance-edited');
        await editDlg.locator('button:has-text("保存")').first().click();
        await sleep(2500);
        const t = (await toasts(page)).join(' | ');
        edited = /成功/.test(t) && !/失败/.test(t);
        add('agents.editSubAgent.ok', edited, `toast="${t}"`);
      } else add('agents.editSubAgent.ok', false, '编辑弹窗未出现');
    }

    // 7) 删除（清理）—— 先取消一次证明确认框在，再确认删除
    let deleted = false;
    if ((await rowAfter.count()) > 0) {
      await rowAfter.first().locator('button:has-text("删除")').first().click();
      await sleep(1000);
      const mb = page.locator('.el-message-box').first();
      add('agents.deleteConfirm.shown', (await mb.count()) > 0, '删除确认框');
      await mb.locator('button:has-text("确定")').first().click();
      await sleep(2500);
      const t = (await toasts(page)).join(' | ');
      deleted = /删除成功|成功/.test(t) && !/失败/.test(t);
      add('agents.deleteSubAgent.ok', deleted, `toast="${t}"`);
    }
    await sleep(800);
    const rowGone = await tab.locator('tr', { hasText: SUB_NAME }).count();
    add('agents.subRow.gone', rowGone === 0, `删除后含 ${SUB_NAME} 的行=${rowGone}`);

    add('gate.jsErrors', jsErr.length === 0, jsErr.slice(0, 4).join(' | ') || 'none');
  } catch (e) {
    add('script.fatal', false, (e && e.message) || String(e));
  } finally {
    const report = {
      generatedAt: new Date().toISOString(), base: BASE, agent: AGENT_NAME,
      passed: checks.filter((c) => c.ok).length, failed: checks.filter((c) => !c.ok).length,
      subAgentRequests: subReq, detailRequests: detailReq, jsErrors: jsErr, checks,
    };
    fs.mkdirSync(path.dirname(OUT), { recursive: true });
    fs.writeFileSync(OUT, JSON.stringify(report, null, 2));
    await browser.close().catch(() => {});
    console.log('\n===== /agents 子Agent 定向验收 =====');
    console.log(`  PASS=${report.passed} FAIL=${report.failed}  out=${OUT}`);
    console.log('  sub-agents 请求: ' + (subReq.join(' | ') || '(无)'));
    process.exit(report.failed ? 1 : 0);
  }
})();
