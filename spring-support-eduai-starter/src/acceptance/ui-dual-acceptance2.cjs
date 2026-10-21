const fs = require('fs');
let pw; try { pw = require('playwright'); } catch (e) { pw = require('D:/ch/project/vue-support-parent-starter/node_modules/playwright'); }
const BASE = 'http://192.168.50.198:19093';
const OUT = 'D:/ch/project/spring-support-parent-starter/spring-support-eduai-starter/src/acceptance/_dual-acceptance2.txt';
const log = (...a) => { const line = a.join(' '); console.log(line); fs.appendFileSync(OUT, line + '\n'); };
const RESULT = { student: [], teacher: [] };
const add = (side, name, ok, detail) => {
  RESULT[side].push({ name, ok: !!ok, detail: detail || '' });
  log((ok ? '  PASS  ' : '  FAIL  ') + side + ' · ' + name + (detail ? '   [' + detail + ']' : ''));
};

async function studentLogin(p) {
  await p.goto(BASE + '/edu/student-login', { waitUntil: 'domcontentloaded', timeout: 60000 });
  await p.waitForSelector('.sl-form input[type="text"]', { timeout: 60000 });
  await p.locator('.sl-field__box input[type="text"]').first().fill('2026000004');
  await p.locator('.sl-field__box input[type="password"]').first().fill('123456');
  await p.locator('button.sl-submit').first().click();
  await p.waitForTimeout(7000);
}

async function teacherLogin(p) {
  await p.goto(BASE + '/edu/student-login', { waitUntil: 'domcontentloaded', timeout: 60000 });
  await p.waitForSelector('.sl-form input[type="text"]', { timeout: 60000 });
  await p.locator('.sl-field__box input[type="text"]').first().fill('ACC-1791086381');
  await p.locator('.sl-field__box input[type="password"]').first().fill('Teach@12345');
  await p.locator('button.sl-submit').first().click();
  await p.waitForTimeout(8000);
}

async function provingStudent(ctx) {
  const p = await ctx.newPage();
  log('=== 学生端验收 ===');
  await studentLogin(p);
  const path = await p.evaluate(() => location.pathname);
  add('student', '统一登录成功', path === '/edu/student', path);

  await p.goto(BASE + '/edu/student?m=wrongbook', { waitUntil: 'domcontentloaded', timeout: 60000 });
  await p.waitForFunction(() => document.querySelectorAll('.wb-card').length > 0 || (document.body.innerText || '').includes('还没有错题'), null, { timeout: 30000 }).catch(() => {});
  await p.waitForTimeout(1500);

  // 首次登录改密弹窗：填新密码并提交（学生账号被我重置过，mustChangePassword=true）
  const pwdModal = p.locator('.el-dialog', { hasText: '设置我的密码' });
  if (await pwdModal.count()) {
    const inputs = pwdModal.locator('input[type="password"]');
    const cnt = await inputs.count();
    if (cnt >= 2) {
      await inputs.nth(0).fill('New123456');
      await inputs.nth(1).fill('New123456');
      await pwdModal.locator('button', { hasText: '设置我的密码' }).click();
      await p.waitForTimeout(3000);
    }
  }
  await p.waitForTimeout(1000);

  const state0 = await p.evaluate(() => ({
    title: /我的错题本/.test(document.body.innerText || ''),
    cards: document.querySelectorAll('.wb-card').length,
    correctBtns: Array.from(document.querySelectorAll('.wb-card button')).filter((b) => (b.innerText || '').trim() === '订正').length,
    doneBtns: Array.from(document.querySelectorAll('.wb-card button')).filter((b) => (b.innerText || '').trim() === '已订正').length,
  }));
  add('student', '错题本页面加载', state0.title, 'cards=' + state0.cards);
  add('student', '错题卡片存在（含种子）', state0.cards >= 2, 'cards=' + state0.cards);
  add('student', '订正/已订正按钮共存', state0.correctBtns >= 1 && state0.doneBtns >= 1, '订正=' + state0.correctBtns + ' 已订正=' + state0.doneBtns);

  // 点击第一张卡的「订正」，期望按钮变为「已订正」
  if (state0.correctBtns >= 1) {
    await p.locator('.wb-card button:has-text("订正"):not(:has-text("已订正"))').first().click();
    await p.waitForTimeout(1500);
    const state1 = await p.evaluate(() => ({
      correctBtns: Array.from(document.querySelectorAll('.wb-card button')).filter((b) => (b.innerText || '').trim() === '订正').length,
      doneBtns: Array.from(document.querySelectorAll('.wb-card button')).filter((b) => (b.innerText || '').trim() === '已订正').length,
    }));
    add('student', '订正动作生效（订正变已订正）', state0.correctBtns - state1.correctBtns >= 1 && state1.doneBtns >= state0.doneBtns, JSON.stringify({ before: state0.correctBtns, after: state1.correctBtns }));
  }
  await p.close();
}

async function provingTeacher(ctx) {
  const p = await ctx.newPage();
  log('=== 教师端验收 ===');
  await teacherLogin(p);
  const path = await p.evaluate(() => location.pathname);
  add('teacher', '教师登录进入老师端', path === '/edu/teacher', path);

  await p.goto(BASE + '/edu/teacher', { waitUntil: 'domcontentloaded', timeout: 60000 });
  await p.waitForTimeout(6000);
  let tab = p.locator('.el-tabs__item, [role="tab"]', { hasText: '阅卷' }).first();
  if (await tab.count()) { await tab.click(); await p.waitForTimeout(3000); }

  const wb = await p.evaluate(() => {
    const t = document.body.innerText || '';
    return {
      text: t.replace(/\s+/g, ' ').slice(0, 260),
      hasBatch: Array.from(document.querySelectorAll('button')).some((b) => (b.innerText || '').trim() === '批量完成'),
      hasBatchUndo: Array.from(document.querySelectorAll('button')).some((b) => (b.innerText || '').trim() === '批量撤销'),
      hasStatsCard: /任务总数|订正率|错题总数/.test(t),
      hasFinishFilter: /待完成|已完成/.test(t),
    };
  });
  add('teacher', '阅卷工作台进入', /AI 阅卷记录|批量完成|任务总数/.test(wb.text), wb.text.slice(0, 100));
  add('teacher', '批量完成按钮', wb.hasBatch, '');
  add('teacher', '批量撤销按钮', wb.hasBatchUndo, '');
  add('teacher', '总览统计卡', wb.hasStatsCard, '');
  add('teacher', '完成状态筛选', wb.hasFinishFilter, '');
  await p.close();
}

(async () => {
  fs.writeFileSync(OUT, '');
  const b = await pw.chromium.launch({ headless: true });
  await provingStudent(await b.newContext({ viewport: { width: 1440, height: 900 } }));
  await provingTeacher(await b.newContext({ viewport: { width: 1440, height: 900 } }));

  const failed = RESULT.student.filter((r) => !r.ok).length + RESULT.teacher.filter((r) => !r.ok).length;
  log('RESULT_JSON ' + JSON.stringify({ failed, student: RESULT.student, teacher: RESULT.teacher }));
  await b.close();
  process.exit(failed === 0 ? 0 : 3);
})().catch((e) => { log('FATAL ' + (e.stack || e)); process.exit(1); });