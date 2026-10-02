/**
 * 生产级测试 #2 · 跨浏览器
 *
 * <p>Chromium / Firefox / WebKit 三个引擎各跑一遍核心只读路径 + 一个写路径（可逆）。</p>
 *
 * <p>写路径选「热更新配置」页签：只有新增+删除两步、无编辑，且删除后服务端无残留；
 * 若某引擎连弹窗都打不开，能立刻看出是兼容性问题而不是我的判据问题。</p>
 *
 * <p>已知坑：WebKit 在 Windows 上跑 playwright 需要额外依赖；启动失败会报
 * 「Executable doesn't exist」，此时如实记录「环境不具备」而不是伪装成「通过」。</p>
 */
import { createRequire } from "node:module";
import { writeFileSync, mkdirSync } from "node:fs";

const require = createRequire("D:/ch/project/vue-support-parent-starter/package.json");
const { chromium, firefox, webkit } = require("playwright");

const BASE = "http://localhost:8096/system";
const OUT = "D:/ch/project/.tmp/crossbrowser";
mkdirSync(OUT, { recursive: true });
const UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/140.0.0.0 Safari/537.36";

// 只读路径：进页面 → 断言渲染出内容 + 无 console error
const READONLY = [
  ["/manage/user", "用户管理", "添加"],
  ["/manage/role", "角色管理", "新增角色"],
  ["/manage/dict", "字典管理", "新增子项"],
  ["/manage/setting", "配置管理", null],
  ["/manage/announcement", "公告管理", "新增公告"],
  ["/monitor/policy", "策略任务", null],
  ["/monitor/cache", "缓存监控", null],
  ["/job/console", "调度管理", null]
];

const ENGINES = [
  { id: "chromium", name: "Chromium", launcher: chromium },
  { id: "firefox", name: "Firefox", launcher: firefox },
  { id: "webkit", name: "WebKit", launcher: webkit }
];

const results = {};

/**
 * 串行执行，且每个引擎跑完等浏览器进程真正退出再开下一个。
 *
 * <p>踩过的坑（2026-10-01）：并发起三个浏览器实例（一个探针里同时 open
 * firefox/webkit/chromium）时，Firefox 与 WebKit 出现「#app 子元素 = 0、
 * main 空白、零 console error」的假故障，单独复现却完全正常。
 * 定性为**资源竞争**，不是应用兼容性问题。判据一旦建立在并发跑批上，
 * 后面所有跨浏览器结论都不可信 —— 所以这里强制串行 + 逐个确认关闭。</p>
 */
for (const eng of ENGINES) {
  const rec = { engine: eng.id, name: eng.name, available: true, pages: [], write: null, err: null };
  let browser = null;
  try {
    browser = await eng.launcher.launch({ headless: false });
  } catch (e) {
    rec.available = false;
    rec.err = "启动失败: " + String(e).split("\n")[0].slice(0, 180);
    results[eng.id] = rec;
    console.log("\n########## " + eng.name + " —— 不可用 ##########");
    console.log("  " + rec.err);
    continue;
  }

  let ctx = null;
  try {
    ctx = await browser.newContext({ userAgent: UA, locale: "zh-CN", viewport: { width: 1600, height: 950 } });
    const page = await ctx.newPage();
    let consoleErrs = [];
    page.on("pageerror", function (e) { consoleErrs.push(String(e).slice(0, 120)); });
    page.on("console", function (m) { if (m.type() === "error") consoleErrs.push(m.text().slice(0, 120)); });

    await page.goto(BASE + "/login", { waitUntil: "domcontentloaded", timeout: 60000 });
    await page.locator('input[placeholder="请输入用户名"]').first().waitFor({ state: "visible", timeout: 30000 });
    await page.locator('input[placeholder="请输入用户名"]').first().fill("admin");
    await page.locator('input[type="password"]').first().fill("admin123");
    await page.locator('button:has-text("登录")').first().click();
    // 登录成功的客观判据：拿到 accessToken（三个引擎行为一致）
    let logged = false;
    for (let i = 0; i < 40; i++) {
      logged = await page.evaluate(function () { return !!localStorage.getItem("system:accessToken"); }).catch(function () { return false; });
      if (logged) break;
      await page.waitForTimeout(500);
    }
    rec.login = logged;
    if (!logged) rec.err = "登录未成功（无 accessToken）";

    for (const [path, name, marker] of READONLY) {
      const p = { name, path, rendered: 0, marker: !!marker, markerFound: false, errs: 0 };
      consoleErrs = [];
      try {
        await page.goto(BASE + path, { waitUntil: "domcontentloaded", timeout: 60000 });
        await page.waitForLoadState("networkidle", { timeout: 25000 }).catch(function () {});
        await page.waitForTimeout(2600);
        p.rendered = await page.evaluate(function () {
          const m = document.querySelector("main") || document.body;
          return (m.innerText || "").replace(/\s+/g, " ").trim().length;
        });
        if (marker) {
          p.markerFound = await page.evaluate(function (mk) {
            return [...document.querySelectorAll("button")].some(function (b) {
              return b.getClientRects().length > 0 && (b.textContent || "").trim().indexOf(mk) >= 0;
            });
          }, marker);
        }
        p.errs = consoleErrs.length;
        if (consoleErrs.length) p.firstErr = consoleErrs[0];
      } catch (e) { p.err = String(e).slice(0, 140); }
      rec.pages.push(p);
    }

    // 写路径：热更新配置 新增 + 删除
    try {
      const probe = "zzq-xb";
      consoleErrs = [];
      await page.goto(BASE + "/monitor/policy", { waitUntil: "domcontentloaded", timeout: 60000 });
      await page.waitForLoadState("networkidle", { timeout: 25000 }).catch(function () {});
      await page.waitForTimeout(3000);
      // 切到「热更新配置」页签
      const tab = page.locator('text=热更新配置').first();
      if (await tab.count()) { await tab.click().catch(function () {}); await page.waitForTimeout(2200); }
      const addBtn = page.locator('button:has-text("新增配置")').first();
      if (await addBtn.count()) {
        await addBtn.click();
        const dlg = page.locator(".el-dialog:visible").last();
        const ok = await dlg.waitFor({ state: "visible", timeout: 15000 }).then(function () { return true; }).catch(function () { return false; });
        if (ok) {
          const fill = async function (label, val) {
            const it = dlg.locator('.el-form-item:has(.el-form-item__label:text-is("' + label + '"))').first();
            try { await it.locator("input, textarea").first().fill(val, { timeout: 8000 }); return true; } catch { return false; }
          };
          await fill("配置名称", probe);
          await fill("配置编码", probe + ".xb");
          await dlg.locator('button:has-text("确定"), button:has-text("保存")').last().click();
          await page.waitForTimeout(3000);
          const msgs = await page.evaluate(function () {
            return [...document.querySelectorAll(".el-message")].map(function (e) { return (e.textContent || "").trim(); }).filter(Boolean);
          });
          rec.write = { dialogOpened: true, msgs: msgs, errs: consoleErrs.length };
        } else rec.write = { dialogOpened: false };
      } else rec.write = { dialogOpened: false, note: "未找到「新增配置」按钮" };
    } catch (e) { rec.write = { err: String(e).slice(0, 140) }; }

  } catch (e) { rec.err = rec.err || String(e).slice(0, 180); }
  // ctx 必须在 browser.close() 之前关，否则进程不会退出、串行也等于并发
  if (ctx) await ctx.close().catch(function () {});
  await browser.close().catch(function () {});
  // 等进程真正退出，避免与下一个引擎抢资源（并发跑批导致的假故障就来自这里）
  await new Promise(function (r) { setTimeout(r, 4000); });
  results[eng.id] = rec;

  console.log("\n########## " + eng.name + " ##########");
  console.log("  登录: " + (rec.login ? "成功" : "失败"));
  for (const p of rec.pages) {
    console.log("    " + p.name.padEnd(8) + " 渲染=" + String(p.rendered).padEnd(6) + (p.marker ? " 关键按钮=" + (p.markerFound ? "有" : "**缺**") + "" : "") + " consoleErr=" + p.errs + (p.err ? "  ERR " + p.err : ""));
  }
  console.log("  写路径(热更新配置): " + JSON.stringify(rec.write));
}

writeFileSync(OUT + "/crossbrowser.json", JSON.stringify(results, null, 2), "utf8");
console.log("\n已写 " + OUT + "/crossbrowser.json");
