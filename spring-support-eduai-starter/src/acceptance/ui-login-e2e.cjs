/*
 * UI 级登录验收（真实浏览器驱动）：走真实登录页 —— 填工号/口令 + 拖动滑块 + 点「登录」，
 * 并抓取 /v2/user/login 的原始响应作为身份证据。
 *
 * 覆盖：
 *   反例  工号 + 错误口令 -> 必须留在登录页并给出业务提示，且不得产生令牌
 *   正例  工号 + 正确口令 -> 离开登录页、令牌落地、userInfo 身份为工号 + TEACHER
 *
 * 运行： node ui-login-e2e.cjs [baseUrl]
 */
const fs = require('fs');
const path = require('path');

function loadPlaywright() {
  const candidates = [
    'playwright',
    'D:/ch/project/vue-support-parent-starter/node_modules/playwright',
    'D:/ch/project/spring-support-parent-starter/node_modules/playwright',
  ];
  for (const c of candidates) {
    try { return require(c); } catch (e) { /* try next */ }
  }
  throw new Error('playwright not found in any candidate path');
}

const { chromium } = loadPlaywright();

const BASE = process.argv[2] || 'http://192.168.50.198:19093';
const TEACHER_NO = 'ACC-1791086381';
const GOOD_PWD = 'Teach@12345';
const BAD_PWD = 'definitely-wrong';
const SHOT_DIR = path.join(process.env.TEMP || '.', 'opencode', 'eduai-login-ui');

fs.mkdirSync(SHOT_DIR, { recursive: true });

function decodeJwt(t) {
  try {
    const p = t.split('.')[1].replace(/-/g, '+').replace(/_/g, '/');
    return JSON.parse(Buffer.from(p, 'base64').toString('utf8'));
  } catch (e) { return { decodeError: String(e.message) }; }
}

async function state(page) {
  return page.evaluate(() => {
    const pick = (k) => localStorage.getItem(k);
    const tokKey = Object.keys(localStorage).find((k) => /token/i.test(k));
    const sliders = Array.from(document.querySelectorAll('.slider-verify')).map((s, i) => {
      const r = s.getBoundingClientRect();
      const t = s.querySelector('.slider-verify-thumb');
      return { i, w: Math.round(r.width), h: Math.round(r.height), x: Math.round(r.x), y: Math.round(r.y),
               inView: r.width > 0 && r.height > 0 && r.top < innerHeight && r.bottom > 0,
               thumbCls: t ? String(t.className) : null };
    });
    return {
      href: location.href,
      hasShell: !!document.querySelector('main.el-main'),
      messages: Array.from(document.querySelectorAll('.el-message, .el-notification, .el-form-item__error'))
        .map((x) => (x.innerText || '').replace(/\s+/g, ' ').trim()).filter(Boolean),
      sliderCount: sliders.length,
      sliders,
      tokenKey: tokKey || null,
      tokenRaw: tokKey ? pick(tokKey) : null,
    };
  });
}

/** 选出登录表单里那个：可见、最宽、且有 thumb */
async function pickFormSlider(page) {
  const idx = await page.evaluate(() => {
    let best = -1, bestW = 0;
    document.querySelectorAll('.slider-verify').forEach((s, i) => {
      const r = s.getBoundingClientRect();
      const vis = r.width > 0 && r.height > 0 && r.top < innerHeight && r.bottom > 0;
      if (vis && r.width > bestW && s.querySelector('.slider-verify-thumb')) { best = i; bestW = r.width; }
    });
    return best;
  });
  return idx;
}

/**
 * 登录页挂载时有一块全屏 `.loading-box` 遮罩（ReLoading，position:fixed/z-index:9999）。
 * 它按 el-fade-in-linear 淡出（约 2.3-3s），期间仍占 100vw×100vh 并会吞掉真实指针事件；
 * 必须等它真正让出命中（rect 归零 / 不再位于命中栈顶）后才做真实鼠标操作。
 * 返回从导航到表单可交互的毫秒数（该时长本身也是可观测项）。
 */
async function waitForInteractiveLogin(page) {
  const t0 = Date.now();
  await page.waitForFunction(() => {
    const s = document.querySelector('.slider-verify');
    if (!s) return false;
    const t = s.querySelector('.slider-verify-thumb');
    if (!t) return false;
    const r = t.getBoundingClientRect();
    if (!(r.width > 0 && r.height > 0)) return false;
    const el = document.elementFromPoint(r.x + r.width / 2, r.y + r.height / 2);
    return !!el && (s.contains(el) || el === s);
  }, null, { timeout: 40000, polling: 100 });
  return Date.now() - t0;
}

async function runCase(browser, { tag, pwd, expect }) {
  const ctx = await browser.newContext({ viewport: { width: 1440, height: 900 } });
  const page = await ctx.newPage();
  const consoleErrors = [];
  const loginResponses = [];
  page.on('console', (m) => { if (m.type() === 'error') consoleErrors.push(m.text().slice(0, 200)); });
  page.on('response', async (res) => {
    if (res.url().includes('/v2/user/login')) {
      let body = null;
      try { body = await res.json(); } catch (e) { body = { parseError: true }; }
      loginResponses.push({ status: res.status(), body });
    }
  });

  await page.goto(BASE + '/login', { waitUntil: 'domcontentloaded' });
  await page.waitForSelector('input[type="password"]', { timeout: 20000 });

  // 等 loading 遮罩真正让出命中，再做真实鼠标操作（否则拖拽被它吞掉）
  const interactiveAfterMs = await waitForInteractiveLogin(page);

  await page.getByPlaceholder('\u8BF7\u8F93\u5165\u7528\u6237\u540D').fill(TEACHER_NO);
  await page.getByPlaceholder('\u8BF7\u8F93\u5165\u5BC6\u7801').fill(pwd);
  await page.waitForTimeout(300);

  // --- 拖滑块（真实鼠标事件；页面可能有多个 .slider-verify，取表单里那个） ---
  const slider = await pickFormSlider(page);
  if (slider < 0) throw new Error('no visible form slider found');
  const wrap = page.locator('.slider-verify').nth(slider);
  const thumb = wrap.locator('.slider-verify-thumb');
  await wrap.waitFor({ state: 'visible', timeout: 10000 });
  const tb = await thumb.boundingBox();
  const wb = await wrap.boundingBox();
  const y = wb.y + wb.height / 2;
  const x0 = tb.x + tb.width / 2;
  const endX = wb.x + wb.width - tb.width / 2 - 1;
  await page.mouse.move(x0, y);
  await page.mouse.down();
  const steps = 22;
  for (let i = 1; i <= steps; i++) {
    await page.mouse.move(x0 + (endX - x0) * (i / steps), y);
    await page.waitForTimeout(18);
  }
  await page.waitForTimeout(80);
  await page.mouse.up();
  await page.waitForTimeout(900);
  const sliderCls = await page.evaluate((i) => {
    const s = document.querySelectorAll('.slider-verify')[i];
    const t = s && s.querySelector('.slider-verify-thumb');
    return t ? String(t.className) : null;
  }, slider);
  const sliderOk = !!(sliderCls && sliderCls.includes('success'));

  await page.locator('button.el-button--primary').first().click();
  await page.waitForTimeout(5000);

  const st = await state(page);
  const token = st.tokenRaw;
  const payload = token ? decodeJwt(token) : null;
  const userInfo = payload ? (payload.userInfo || payload) : null;

  const shot = path.join(SHOT_DIR, tag + '.png');
  await page.screenshot({ path: shot, fullPage: false });

  const out = {
    tag, expect,
    url: st.href,
    leftLogin: !st.href.includes('/login'),
    hasShell: st.hasShell,
    messages: st.messages,
    sliderCount: st.sliderCount,
    sliders: st.sliders,
    formInteractiveAfterMs: interactiveAfterMs,
    sliderClassAfterDrag: sliderCls,
    sliderPassed: sliderOk,
    tokenLen: token ? token.length : 0,
    tokenPayloadKeys: payload ? Object.keys(payload).slice(0, 14) : [],
    identity: userInfo ? {
      sysUserUsername: userInfo.sysUserUsername,
      sysUserId: userInfo.sysUserId,
      sysUserNickname: userInfo.sysUserNickname,
      roles: userInfo.roles,
      accountType: userInfo.accountType,
    } : null,
    loginApi: loginResponses.map((r) => ({
      status: r.status,
      code: r.body && r.body.code,
      msg: r.body && r.body.msg,
      userInfo: r.body && r.body.data && r.body.data.userInfo,
      tokenLen: r.body && r.body.data && r.body.data.accessToken ? String(r.body.data.accessToken).length : 0,
    })),
    consoleErrors,
    screenshot: shot,
  };
  await ctx.close();
  return out;
}

(async () => {
  const browser = await chromium.launch({ headless: true });
  const results = [];
  results.push(await runCase(browser, { tag: 'neg-wrong-password', pwd: BAD_PWD, expect: 'business rejection, stay on /login, no token' }));
  results.push(await runCase(browser, { tag: 'pos-teacher-account', pwd: GOOD_PWD, expect: 'login succeeds, token issued, identity = account + TEACHER' }));
  await browser.close();
  console.log(JSON.stringify(results, null, 2));
})().catch((e) => { console.error('FATAL', e && e.stack || e); process.exit(1); });
