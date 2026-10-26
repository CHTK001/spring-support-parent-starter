/**
 * 阅卷增强补充验收：
 * 1) 工作台「导出成绩单」按钮存在，点击后下载 CSV 且包含成绩单表头
 * 2) 判分详情每题提供「采用AI」，点击后保存按钮变为可提交
 *
 * 用法：node verify-marking-enhancement-ui.cjs <base> <taskId>
 */
const fs = require('fs');
const path = require('path');
let pw;
try {
  pw = require('playwright');
} catch {
  pw = require('D:/ch/project/vue-support-parent-starter/node_modules/playwright');
}

const BASE = process.argv[2] || 'http://127.0.0.1:19093';
const TASK = process.argv[3] || '5482679715c34a99af32b3996a6530db';
const TEACHER = { account: 'ACC-1791086381', pass: 'Teach@12345' };
const SHOT_DIR = path.join(process.env.TEMP || 'D:/ch/project/.tmp', 'opencode', 'marking-enhancement-ui');
fs.mkdirSync(SHOT_DIR, { recursive: true });

const checks = [];
const record = (name, ok, detail) => {
  checks.push({ name, ok: !!ok, detail: String(detail ?? '') });
  console.log(`  ${ok ? 'PASS' : 'FAIL'}  ${name}${ok ? '' : '   [' + detail + ']'}`);
};

async function saveDownloadOrFallback(p, btn, shotDir) {
  let downloadPath = null;
  const downloadPromise = p.waitForEvent('download', { timeout: 15000 }).catch(() => null);
  await btn.click();
  await p.waitForTimeout(1200);
  const download = await downloadPromise;
  if (download) {
    const file = path.join(shotDir, download.suggestedFilename() || 'scores.csv');
    await download.saveAs(file);
    return file;
  }
  return null;
}

(async () => {
  const browser = await pw.chromium.launch({ headless: true });
  const ctx = await browser.newContext({ viewport: { width: 1600, height: 1000 }, acceptDownloads: true });
  const p = await ctx.newPage();
  const errs = [];
  const badResp = [];
  p.on('pageerror', (e) => errs.push(String(e.message || e).slice(0, 180)));
  p.on('console', (m) => { if (m.type() === 'error') errs.push(String(m.text()).slice(0, 180)); });
  p.on('response', (r) => { if (r.status() >= 400) badResp.push(r.status() + ' ' + r.url().replace(/^https?:\/\/[^/]+/, '')); });

  await p.goto(BASE + '/edu/student-login', { waitUntil: 'domcontentloaded', timeout: 60000 });
  await p.waitForSelector('.sl-field__box input[type="text"]', { timeout: 60000 });
  await p.locator('.sl-field__box input[type="text"]').first().fill(TEACHER.account);
  await p.locator('.sl-field__box input[type="password"]').first().fill(TEACHER.pass);
  await p.locator('button.sl-submit').first().click();
  await p.waitForTimeout(5000);

  // 工作台导出成绩单：先等列表行加载完成，再取第一条任务号供详情验证
  let discoveredTask = '';
  await p.goto(BASE + '/edu/marking', { waitUntil: 'domcontentloaded', timeout: 60000 });
  await p.waitForSelector('.mw-records .el-table__row', { timeout: 60000 });
  await p.waitForTimeout(2000);
  discoveredTask = await p.evaluate(() => {
    const row = document.querySelector('.mw-records .el-table__row');
    const td = row?.querySelectorAll('td')[1];
    return (td?.innerText || '').replace(/\s+/g, ' ').trim() || '';
  });
  const listProbe = await p.evaluate(() => ({
    rows: document.querySelectorAll('.mw-records .el-table__row').length,
    trs: document.querySelectorAll('.mw-records .el-table__body tr').length,
    path: location.pathname,
  }));
  console.log('LIST_PROBE ' + JSON.stringify(listProbe) + ' task=' + discoveredTask);
  const exportBtn = p.locator('.mw-batch-bar button').filter({ hasText: '导出成绩单' }).first();
  record('marking workbench has export button', (await exportBtn.count()) > 0, String(await exportBtn.count()));
  let csvHasHeader = false;
  let downloadName = '';
  let exportMsg = '';
  if (await exportBtn.count()) {
    exportMsg = await p.evaluate(() => Array.from(document.querySelectorAll('.el-message')).map((x) => (x.innerText || '').trim()).join(' | ')).catch(() => '');
    const file = await saveDownloadOrFallback(p, exportBtn, SHOT_DIR);
    if (file) {
      downloadName = path.basename(file);
      const text = fs.readFileSync(file, 'utf8');
      csvHasHeader = text.includes('任务号') && text.includes('学生') && text.includes('AI 总分') && text.includes('老师总分');
    }
    exportMsg = await p.evaluate(() => Array.from(document.querySelectorAll('.el-message')).map((x) => (x.innerText || '').trim()).join(' | ')).catch(() => '') || exportMsg;
  }
  record('export score CSV download has header', csvHasHeader, 'file=' + downloadName + ' msg=' + exportMsg);
  const exportTask = process.argv[3] || discoveredTask || TASK;
  record('discovered a teacher-scoped task from list', !!(discoveredTask && discoveredTask.length > 0), discoveredTask);

  // 判分详情一键采用 AI
  await p.goto(BASE + `/edu/marking/detail?taskId=${encodeURIComponent(exportTask)}`, { waitUntil: 'domcontentloaded', timeout: 60000 });
  await p.waitForSelector('.sheet-structure, .ss-panel, .marking-detail', { timeout: 60000 });
  await p.waitForTimeout(3500);
  const applyCount = await p.locator('.ss-comment-cell button').filter({ hasText: '采用AI' }).count();
  record('detail rows expose 采用AI button', applyCount > 0, 'count=' + applyCount + ' task=' + exportTask);
  let applyEnabled = false;
  let saveEnabledAfter = null;
  if (applyCount > 0) {
    const applyBtn = p.locator('.ss-comment-cell button:not(.is-disabled)').filter({ hasText: '采用AI' }).first();
    applyEnabled = (await applyBtn.count()) > 0;
    const saveBtn = p.locator('.ss-panel-actions button, .ss-actions button').filter({ hasText: '保存老师分' }).first();
    if (applyEnabled && (await saveBtn.count())) {
      await applyBtn.click();
      await p.waitForTimeout(600);
      saveEnabledAfter = !(await saveBtn.isDisabled());
    }
  }
  record('采用AI enables save teacher score', applyEnabled && saveEnabledAfter === true,
    `buttons=${applyCount} enabled=${applyEnabled} save=${saveEnabledAfter}`);

  // 全部采用 AI：一键把当前试卷所有可用 AI 分/评语落到老师分/评语
  let allApplyWorks = false;
  let allApplyMsg = '';
  const allApplyBtn = p.locator('.ss-actions button').filter({ hasText: '全部采用AI' }).first();
  if (await allApplyBtn.count()) {
    const saveBtn = p.locator('.ss-actions button').filter({ hasText: '保存老师分' }).first();
    const clickable = await allApplyBtn.isEnabled().catch(() => false);
    if (clickable) {
      await allApplyBtn.click();
      await p.waitForTimeout(800);
      const dirtyAfter = saveBtn.count() ? !(await saveBtn.isDisabled()) : null;
      const msgsLive = await p.evaluate(() => Array.from(document.querySelectorAll('.el-message')).map((x) => (x.innerText || '').trim()).join(' | ')).catch(() => '');
      allApplyMsg = msgsLive || allApplyMsg;
      allApplyWorks = dirtyAfter === true;
    }
  }
  record('全部采用AI enables save teacher score', allApplyWorks, 'msg=' + allApplyMsg);

  // 完成预览：应包含判错阈值口径说明（后端已在 VO 提供 wrongRate）
  let previewThresholdShown = false;
  let previewText = '';
  try {
    const finishBtn = p.locator('.mr-sum button, .mr-sum .mr-finish-wrap button').filter({ hasText: '完成' }).first();
    if (await finishBtn.count()) {
      await finishBtn.click();
      await p.waitForSelector('.mr-preview-tip, .el-dialog:has-text("阅卷完成预览")', { timeout: 15000 }).catch(() => {});
      await p.waitForTimeout(800);
      previewText = await p.evaluate(() => {
        const tip = document.querySelector('.mr-preview-tip');
        return tip ? (tip.innerText || '').replace(/\s+/g, ' ').trim() : '';
      }).catch(() => '');
      previewThresholdShown = previewText.includes('判错口径') && (previewText.includes('<') || previewText.includes('0%'));
      // 关闭预览，避免真正完成
      const cancel = p.locator('.el-dialog button').filter({ hasText: '取消' }).last();
      if (await cancel.count()) await cancel.click();
    }
  } catch (e) { /* noop */ }
  record('finish preview shows wrong-rate threshold', previewThresholdShown, previewText);

  const unexpectedErr = errs.filter((x) => !/Failed to load resource|ResizeObserver|vite|favicon/i.test(x));
  record('no unexpected JS errors', unexpectedErr.length === 0, JSON.stringify(unexpectedErr.slice(0, 4)));
  const harmless404 = ['/typhoon-api/TyhoonActivity'];
  const filteredBad = badResp.filter((x) => !harmless404.some((h) => x.includes(h)));
  record('no unexpected HTTP >= 400', filteredBad.length === 0, JSON.stringify(filteredBad.slice(0, 5)));

  const shot = path.join(SHOT_DIR, 'marking-enhancement-' + Date.now() + '.png');
  await p.screenshot({ path: shot, fullPage: false });
  console.log('SCREENSHOT ' + shot);

  const failed = checks.filter((c) => !c.ok).length;
  console.log('RESULT_JSON ' + JSON.stringify({ passed: checks.length - failed, failed, checks }));
  await browser.close();
  process.exit(failed === 0 ? 0 : 3);
})().catch((e) => {
  console.error('FATAL', e.stack || e);
  process.exit(1);
});