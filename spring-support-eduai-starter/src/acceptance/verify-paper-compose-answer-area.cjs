/*
 * 组卷/生成试卷验收：
 * 1) 主观题（SHORT_ANSWER / SUBJECTIVE）行内出现“答题区行数”控件
 * 2) 纸张预览中主观题答题区没有虚线框，且有默认高度
 * 3) 若试卷可编辑，调整行数后答题区高度同步放大
 * 4) 导出弹框包含「已作答卷」模式
 * 5) 购物车可折叠
 * 6) 分值/行高固定在行右下角独立一行
 * 7) 卷面有姓名/学号/班级填写栏
 * 8) 卷面页脚有整卷二维码（内容 PAPER:{paperId}）
 * 9) 页脚富文本 {page}/{total} 被替换成真实页码
 */
const fs = require('fs');
const path = require('path');
let pw;
try { pw = require('playwright'); } catch (e) { pw = require('D:/ch/project/vue-support-parent-starter/node_modules/playwright'); }

const UI = process.argv[2] || 'http://127.0.0.1:19093';
const BASE = UI;
const PAPER_ID = process.argv[3] || '9043';
const TEACHER = { account: 'ACC-1791086381', pass: 'Teach@12345' };
const SHOT_DIR = path.join(process.env.TEMP || 'D:/ch/project/.tmp', 'opencode', 'paper-compose-answer');
fs.mkdirSync(SHOT_DIR, { recursive: true });

const checks = [];
const record = (n, ok, d) => { checks.push({ name: n, ok: !!ok, detail: String(d === undefined ? '' : d) }); console.log((ok ? '  PASS  ' : '  FAIL  ') + n + (ok ? '' : '   [' + d + ']')); };

(async () => {
  const browser = await pw.chromium.launch({ headless: true });
  const ctx = await browser.newContext({ viewport: { width: 1600, height: 1000 } });
  const p = await ctx.newPage();
  const errs = [];
  const badResp = [];
  p.on('pageerror', (e) => errs.push(String(e.message || e).slice(0, 180)));
  p.on('console', (m) => { if (m.type() === 'error') errs.push(String(m.text()).slice(0, 180)); });
  p.on('response', (r) => { if (r.status() >= 400 && !r.url().includes('/v2/edu/dimension/dropdown')) badResp.push(r.status() + ' ' + r.url().replace(/^https?:\/\/[^/]+/, '')); });

  // 教师登录
  await p.goto(BASE + '/edu/student-login', { waitUntil: 'domcontentloaded' });
  await p.waitForSelector('.sl-field__box input[type="text"]', { timeout: 60000 });
  await p.locator('.sl-field__box input[type="text"]').first().fill(TEACHER.account);
  await p.locator('.sl-field__box input[type="password"]').first().fill(TEACHER.pass);
  await p.locator('button.sl-submit').first().click();
  await p.waitForTimeout(5000);
  const landed = await p.evaluate(() => location.pathname);
  record('teacher login landed', landed.includes('/edu') || landed.includes('/login') ? !landed.includes('/login') : true, landed);

  // 打开组卷页
  await p.goto(BASE + '/eduai/paper-compose?paperId=' + PAPER_ID, { waitUntil: 'domcontentloaded' });
  await p.waitForTimeout(6000);
  await p.waitForSelector('.compose-list, .pp-page', { timeout: 60000 }).catch(() => {});

  const state = await p.evaluate(() => {
    const readonly = !!document.querySelector('.compose-readonly');
    const rows = Array.from(document.querySelectorAll('.compose-row'));
    const subjRows = rows.filter((r) => (r.innerText || '').includes('主观') || (r.innerText || '').includes('简答'));
    const lineControls = subjRows.map((r) => ({
      hasControl: !!r.querySelector('.compose-row__answer-lines input'),
      lines: r.querySelector('.compose-row__answer-lines input')?.value || null,
      type: (r.innerText || '').slice(0, 30),
    }));
    const answers = Array.from(document.querySelectorAll('.pp-question__answer'));
    const answerCSS = answers.length
      ? Array.from(new Set(answers.map((a) => {
          const st = getComputedStyle(a);
          return `h=${Math.round(a.getBoundingClientRect().height)} border=${st.borderStyle}`;
        }))).slice(0, 5)
      : [];
    const cart = document.querySelector('.cart-panel');
    const collapseBtn = cart?.querySelector('button');
    const footers = Array.from(document.querySelectorAll('.pp-page'));
    const pageTexts = footers.map((f) => (f.innerText || '').slice(0, 500));
    return {
      readonly,
      rowCount: rows.length,
      subjCount: subjRows.length,
      lineControls,
      answerCount: answers.length,
      answerCSS,
      hasCart: !!cart,
      hasCartCollapse: !!collapseBtn,
      hasFooterScore: rows.some((r) => !!r.querySelector('.compose-row__footer .compose-row__score input')),
      hasFooterLines: rows.some((r) => !!r.querySelector('.compose-row__footer .compose-row__answer-lines input')),
      hasStudentBar: pageTexts.some((t) => t.includes('姓名') && t.includes('学号')),
      hasPaperQr: !!document.querySelector('.pp-footer__qr'),
      qrAlt: document.querySelector('.pp-footer__qr')?.getAttribute('alt') || null,
      literalPageTemplate: pageTexts.join('\n').includes('{page}'),
      footerPagesOk: footers.length > 0 && footers.every((f, i) => (f.innerText || '').includes(`第 ${i + 1} 页 / 共 ${footers.length} 页`)),
    };
  });
  record('compose page loaded with questions', state.rowCount > 0, `rows=${state.rowCount}`);
  record('subjective rows show line control', state.subjCount > 0 && state.lineControls.every((x) => x.hasControl),
    JSON.stringify(state.lineControls.slice(0, 4)));
  record('subjective rows have default lines', state.subjCount > 0 && state.lineControls.some((x) => Number(x.lines) >= 3),
    JSON.stringify(state.lineControls.slice(0, 4)));
  record('paper preview has answer blank areas', state.answerCount > 0, `count=${state.answerCount}`);
  record('answer blank has no dashed border', state.answerCSS.every((x) => !x.includes('dashed')), JSON.stringify(state.answerCSS));
  record('cart panel has collapse button', state.hasCartCollapse, String(state.hasCartCollapse));
  record('score and answer lines are in bottom footer row', state.hasFooterScore && state.hasFooterLines,
    `score=${state.hasFooterScore} lines=${state.hasFooterLines}`);
  record('paper header has student name/number fields', state.hasStudentBar, String(state.hasStudentBar));
  record('paper footer has whole-paper QR', state.hasPaperQr && state.qrAlt && state.qrAlt.includes('' + PAPER_ID),
    'alt=' + state.qrAlt);
  record('page placeholder is replaced with real page numbers', !state.literalPageTemplate && state.footerPagesOk,
    `literal=${state.literalPageTemplate} pages=${state.footerPagesOk}`);

  // 购物车折叠交互
  let cartCollapsedWorks = false;
  const cartPanel = p.locator('.cart-panel');
  if (await cartPanel.count()) {
    const foldBtn = cartPanel.locator('button').last();
    if (await foldBtn.count()) {
      await foldBtn.click();
      await p.waitForTimeout(500);
      const collapsed = await p.locator('.cart-panel.is-collapsed').count();
      await foldBtn.click();
      await p.waitForTimeout(500);
      const restored = (await p.locator('.cart-panel.is-collapsed').count()) === 0;
      cartCollapsedWorks = collapsed > 0 && restored;
    }
  }
  record('cart collapse toggle works', cartCollapsedWorks, String(cartCollapsedWorks));

  // 若是可编辑，试调一个主观题行数并验证预览高度
  let heightChanged = false;
  let beforeH = null;
  let afterH = null;
  if (!state.readonly && state.subjCount > 0) {
    const first = p.locator('.compose-row__answer-lines input').first();
    if (await first.count()) {
      beforeH = await p.locator('.pp-question__answer').first().evaluate((el) => Math.round(el.getBoundingClientRect().height)).catch(() => null);
      await first.fill('6');
      await first.press('Enter');
      await p.waitForTimeout(1000);
      afterH = await p.locator('.pp-question__answer').first().evaluate((el) => Math.round(el.getBoundingClientRect().height)).catch(() => null);
      heightChanged = beforeH != null && afterH != null && afterH > beforeH;
    }
  }
  record('adjusting answer lines changes blank height', state.readonly || heightChanged,
    JSON.stringify({ readonly: state.readonly, beforeH, afterH, heightChanged }));

  // 导出弹框：点击导出按钮
  const exportBtn = p.locator('.paper-panel, .panel-head').filter({ hasText: '导出 A4 图片' }).first();
  let exportVisible = false;
  const openBtn = p.locator('button').filter({ hasText: /^$/ }).first(); // placeholder
  try {
    const candidates = p.locator('button[aria-label="导出 A4 图片"]');
    if (await candidates.count()) {
      await candidates.first().click();
      await p.waitForTimeout(1200);
      const dlg = p.locator('.paper-export-dialog, .el-dialog, .re-dialog').filter({ hasText: '导出内容' }).last();
      exportVisible = !!(await dlg.count()) && await dlg.locator('.el-radio-button, .re-radio-button').filter({ hasText: '已作答卷' }).count();
    }
  } catch (e) { /* noop */ }
  record('export dialog offers answered mode', exportVisible, 'visible=' + exportVisible);

  const shot = path.join(SHOT_DIR, 'compose-' + Date.now() + '.png');
  await p.screenshot({ path: shot, fullPage: false });
  console.log('SCREENSHOT ' + shot);

  // 已作答卷导出不贴标准答案：主观题用模拟文案、客观题随机勾选
  const source = fs.readFileSync(
    path.join(__dirname, '../../../../vue-support-parent-starter/pages/eduai/src/views/PaperComposeView.vue'),
    'utf8'
  );
  const fnStart = source.indexOf('function buildExportAnswers');
  const fnEnd = source.indexOf('const SUBJECTIVE_MOCK_TEXT', fnStart > 0 ? fnStart : 0);
  const answerFnBody = fnStart >= 0 && fnEnd > fnStart ? source.slice(fnStart, fnEnd) : '';
  const answeredUsesMock = answerFnBody.includes('SUBJECTIVE_MOCK_TEXT') && answerFnBody.includes('answers[key] = SUBJECTIVE_MOCK_TEXT');
  const answeredDoesNotReadAnswer = answerFnBody.includes('SUBJECTIVE_MOCK_TEXT') && !answerFnBody.includes('q.answer');
  record('answered export uses mock answers, not standard answer', !!(answeredUsesMock && answeredDoesNotReadAnswer),
    `mock=${answeredUsesMock} noStandardAnswer=${answeredDoesNotReadAnswer} bodyLen=${answerFnBody.length}`);

  const unexpectedErr = errs.filter((x) => !/Failed to load resource|ResizeObserver|vite|favicon/i.test(x));
  record('no unexpected JS errors', unexpectedErr.length === 0, JSON.stringify(unexpectedErr.slice(0, 4)));
  record('no unexpected HTTP >= 400', badResp.length === 0, JSON.stringify(badResp.slice(0, 5)));

  const failed = checks.filter((c) => !c.ok).length;
  console.log('RESULT_JSON ' + JSON.stringify({ passed: checks.length - failed, failed, checks }));
  await browser.close();
  process.exit(failed === 0 ? 0 : 3);
})().catch((e) => { console.error('FATAL', e.stack || e); process.exit(1); });