/*
 * 统一登录页（/edu/student-login，趣味学习登录页）的 UI 级登录验收。
 *
 * 该页是产品里的「统一登录页」：账号框提示「请输入学号 / 工号 / 手机号」，内部按账号形状分流——
 *   纯数字(6~20位) -> studentLogin(accountType=student)
 *   其它          -> 先 teacherLogin(accountType=teacher)，不认再回落系统账号(userLogin)
 * （框架自带的 /login 是后台管理登录页，不带 accountType，不在本验收范围。）
 *
 * 四个真实浏览器用例：工号正例 / 工号错口令 / 学号正例 / 学号错口令，
 * 每个用例都记录：提交请求体、服务端原始响应、落地路由、令牌载荷里的身份与角色。
 *
 * 运行： node ui-unified-login-e2e.cjs [baseUrl]
 */
const path = require('path');
const fs = require('fs');

function loadPlaywright() {
  const candidates = [
    'playwright',
    'D:/ch/project/vue-support-parent-starter/node_modules/playwright',
    'D:/ch/project/spring-support-parent-starter/node_modules/playwright',
  ];
  for (const c of candidates) { try { return require(c); } catch (e) { /* next */ } }
  throw new Error('playwright not found');
}
const { chromium } = loadPlaywright();

const BASE = process.argv[2] || 'http://192.168.50.198:19093';
const SHOT_DIR = path.join(process.env.TEMP || '.', 'opencode', 'eduai-unified-login-ui');
fs.mkdirSync(SHOT_DIR, { recursive: true });

const TEACHER = { account: 'ACC-1791086381', good: 'Teach@12345', bad: 'definitely-wrong', expectHome: '/edu/teacher', expectRole: 'TEACHER' };
const STUDENT = { account: '2026000001', good: 'Stud@12345', bad: 'definitely-wrong', expectHome: '/edu/student', expectRole: 'STUDENT' };

function decodeJwt(t) {
  try {
    const p = t.split('.')[1].replace(/-/g, '+').replace(/_/g, '/');
    return JSON.parse(Buffer.from(p, 'base64').toString('utf8'));
  } catch (e) { return { decodeError: String(e.message) }; }
}

async function readState(page) {
  return page.evaluate(() => {
    const tokKey = Object.keys(localStorage).find((k) => /token/i.test(k));
    return {
      href: location.href,
      inlineErrors: Array.from(document.querySelectorAll('.sl-field__err')).map(x => (x.innerText || '').trim()).filter(Boolean),
      messages: Array.from(document.querySelectorAll('.el-message, .el-notification')).map(x => (x.innerText || '').replace(/\s+/g, ' ').trim()).filter(Boolean),
      tokenKey: tokKey || null,
      tokenRaw: tokKey ? localStorage.getItem(tokKey) : null,
    };
  });
}

async function runCase(browser, c) {
  const ctx = await browser.newContext({ viewport: { width: 1440, height: 900 } });
  const page = await ctx.newPage();
  const consoleErrors = [];
  const api = [];
  page.on('console', (m) => { if (m.type() === 'error') consoleErrors.push(m.text().slice(0, 200)); });
  page.on('request', (r) => { if (r.url().includes('/v2/user/login')) api.push({ dir: 'req', method: r.method(), postData: r.postData() }); });
  page.on('response', async (r) => {
    if (r.url().includes('/v2/user/login')) {
      let body = null; try { body = await r.json(); } catch (e) { body = { parseError: true }; }
      api.push({ dir: 'res', status: r.status(), code: body && body.code, msg: body && body.msg,
                 roles: body && body.data && body.data.roles, tokenLen: body && body.data && body.data.accessToken ? String(body.data.accessToken).length : 0,
                 userInfo: body && body.data && body.data.userInfo ? {
                   sysUserUsername: body.data.userInfo.sysUserUsername,
                   sysUserId: body.data.userInfo.sysUserId,
                   sysUserNickname: body.data.userInfo.sysUserNickname,
                   roles: body.data.userInfo.roles,
                 } : null,
                 userExt: body && body.data && body.data.user && body.data.user.ext });
    }
  });

  await page.goto(BASE + '/edu/student-login', { waitUntil: 'domcontentloaded' });
  await page.waitForSelector('.sl-form input[type="text"]', { timeout: 60000 });
  await page.waitForSelector('.sl-form input[type="password"]', { timeout: 60000 });
  await page.waitForTimeout(600);

  await page.locator('.sl-field__box input[type="text"]').first().fill(c.account);
  await page.locator('.sl-field__box input[type="password"]').first().fill(c.pwd);
  await page.locator('button.sl-submit').first().click();
  await page.waitForTimeout(5000);

  const st = await readState(page);
  const okRes = api.find((x) => x.dir === 'res' && x.code === '00000');
  // 令牌是不透明串（非 JWT），身份证据取自登录响应的 userInfo（与 check-teacher-login.sh 同口径）
  const ui = okRes ? okRes.userInfo : null;

  const shot = path.join(SHOT_DIR, c.tag + '.png');
  await page.screenshot({ path: shot, fullPage: false });

  const out = {
    tag: c.tag, accountKind: c.kind, account: c.account, password: c.pwd === c.goodExpected ? '(good)' : '(bad)',
    expect: c.expect,
    url: st.href,
    landedHome: st.href.replace(/^https?:\/\/[^/]+/, ''),
    inlineErrors: st.inlineErrors,
    messages: st.messages,
    tokenLen: st.tokenRaw ? st.tokenRaw.length : 0,
    identity: ui ? { sysUserUsername: ui.sysUserUsername, sysUserId: ui.sysUserId, sysUserNickname: ui.sysUserNickname, roles: ui.roles, accountType: ui.accountType } : null,
    api,
    consoleErrors,
    screenshot: shot,
  };
  await ctx.close();
  return out;
}

(async () => {
  const browser = await chromium.launch({ headless: true });
  const cases = [
    { tag: 'teacher-account-pos', kind: 'TEACHER 工号', account: TEACHER.account, pwd: TEACHER.good, goodExpected: TEACHER.good, expect: 'login ok -> ' + TEACHER.expectHome + ', token, roles=' + TEACHER.expectRole },
    { tag: 'teacher-account-neg', kind: 'TEACHER 工号', account: TEACHER.account, pwd: TEACHER.bad,  goodExpected: TEACHER.good, expect: 'stays on page, inline error, no token' },
    { tag: 'student-account-pos', kind: 'STUDENT 学号', account: STUDENT.account, pwd: STUDENT.good, goodExpected: STUDENT.good, expect: 'login ok -> ' + STUDENT.expectHome + ', token, roles=' + STUDENT.expectRole },
    { tag: 'student-account-neg', kind: 'STUDENT 学号', account: STUDENT.account, pwd: STUDENT.bad,  goodExpected: STUDENT.good, expect: 'stays on page, inline error, no token' },
  ];
  const results = [];
  const only = process.argv[3];
  for (const c of cases) {
    if (only && c.tag !== only) continue;
    try {
      results.push(await runCase(browser, c));
    } catch (e) {
      results.push({ tag: c.tag, HARDCASE_ERROR: String(e && e.message || e).split('\n')[0] });
    }
    if (!browser.isConnected()) { console.error('browser disconnected, stopping'); break; }
  }
  try { await browser.close(); } catch (e) { /* ignore */ }
  console.log(JSON.stringify(results, null, 2));
})().catch((e) => { console.error('FATAL', e && e.stack || e); process.exit(1); });
