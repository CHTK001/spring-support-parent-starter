/**
 * 权限矩阵 · 菜单可见性
 *
 * <p>服务端网关管住了 156 个端点，但用户能不能<strong>看到</strong>入口是另一回事。
 * 本项用真实浏览器分别以 ADMIN / OPS / USER 登录，采集侧边栏菜单文本做对比。</p>
 *
 * <p>已知坑：新建账号登录后 userInfo.agreementNeedConfirm=true，会被 agreement 拦页，
 * 必须先处理协议确认才能进主壳（否则采到的是协议页而不是菜单）。</p>
 */
import { createRequire } from "node:module";
import { writeFileSync, mkdirSync } from "node:fs";

const require = createRequire("D:/ch/project/vue-support-parent-starter/package.json");
const { chromium } = require("playwright");

const BASE = "http://localhost:8096/system";
const OUT = "D:/ch/project/.tmp/perm-matrix";
const UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/140.0.0.0 Safari/537.36";
mkdirSync(OUT, { recursive: true });

const SUBJECTS = [
  { id: "ADMIN", u: "admin", p: "admin123" },
  { id: "OPS", u: "zzq_ops_01", p: "Zzq2026aa" },
  { id: "USER", u: "zzq_user_01", p: "Zzq2026aa" }
];

const browser = await chromium.launch({ headless: false, args: ["--window-size=1680,1000"] });
const out = {};

for (const s of SUBJECTS) {
  const ctx = await browser.newContext({ userAgent: UA, locale: "zh-CN", viewport: { width: 1600, height: 950 } });
  const page = await ctx.newPage();
  const rec = { subject: s.id, url: null, agreementBlocked: false, menus: [], pageErrors: 0 };
  page.on("pageerror", function () { rec.pageErrors++; });
  try {
    await page.goto(BASE + "/login", { waitUntil: "domcontentloaded", timeout: 60000 });
    await page.locator('input[placeholder="请输入用户名"]').first().waitFor({ state: "visible", timeout: 25000 });
    await page.locator('input[placeholder="请输入用户名"]').first().fill(s.u);
    await page.locator('input[type="password"]').first().fill(s.p);
    await page.locator('button:has-text("登录")').first().click();
    await page.waitForTimeout(4000);
    rec.url = page.url();

    // 协议确认拦页
    const agree = page.locator('button:has-text("同意"), button:has-text("接受"), button:has-text("确认")').first();
    if (await agree.count() && await agree.isVisible().catch(function () { return false; })) {
      rec.agreementBlocked = true;
      await agree.click().catch(function () {});
      await page.waitForTimeout(3500);
      rec.urlAfterAgree = page.url();
    }

    await page.waitForLoadState("networkidle", { timeout: 25000 }).catch(function () {});
    await page.waitForTimeout(3000);

    // 侧边栏默认是收起的最小化 dock（aside 里只有「后台管理系统设置」），
    // 必须先展开工具栏，否则采到 0 项（踩过一次）
    const ex = page.locator('button[aria-label="展开工具栏"]').first();
    rec.dockCollapsed = await ex.count() > 0 && await ex.isVisible().catch(function () { return false; });
    if (rec.dockCollapsed) {
      await ex.click();
      await page.waitForTimeout(2000);
    }
    rec.asideAfter = await page.evaluate(function () {
      const a = document.querySelector("aside");
      return a ? (a.textContent || "").replace(/\s+/g, " ").trim() : null;
    });

    // 判据改用「渲染后的 aside 全文 + 已知菜单名清单做包含判定」。
    // 依赖 class 选择器不可靠：同一个脚本对 ADMIN 采到 0 项、USER 采到 4 项，
    // 而三个角色渲染出来的菜单文本都远多于此 —— 那是采集 bug，不是权限结论。
    const KNOWN = ["工作台", "系统管理", "用户管理", "角色管理", "菜单管理", "机构管理",
      "字典管理", "参数配置", "公告管理", "反馈管理", "岗位管理", "消息管理",
      "监控管理", "监控大屏", "Redis 监控", "缓存监控", "策略任务", "远程节点",
      "调度管理", "作业日志", "文件管理", "登录日志", "系统日志"];
    rec.knownMenus = KNOWN;
    rec.menus = KNOWN.filter(function (k) { return rec.asideAfter && rec.asideAfter.indexOf(k) >= 0; });
    await page.screenshot({ path: OUT + "/menu-" + s.id + ".png" });
  } catch (e) { rec.err = String(e).slice(0, 160); }
  out[s.id] = rec;
  await ctx.close();
  console.log("\n########## " + s.id + " ##########");
  console.log("  落地 URL: " + rec.url + (rec.urlAfterAgree ? "  (同意协议后 -> " + rec.urlAfterAgree + ")" : ""));
  console.log("  被协议页拦截: " + rec.agreementBlocked);
  console.log("  侧栏初始为收起 dock: " + rec.dockCollapsed);
  console.log("  侧栏渲染全文: " + JSON.stringify(rec.asideAfter));
  console.log("  可见菜单(" + rec.menus.length + "/" + rec.knownMenus.length + "): " + JSON.stringify(rec.menus));
  if (rec.err) console.log("  错误: " + rec.err);
}

writeFileSync(OUT + "/menu-visibility.json", JSON.stringify(out, null, 2), "utf8");
console.log("\n=== 与 ADMIN 对比 ===");
const base = out.ADMIN.menus;
for (const s of ["OPS", "USER"]) {
  const missing = base.filter(function (m) { return out[s].menus.indexOf(m) < 0; });
  const extra = out[s].menus.filter(function (m) { return base.indexOf(m) < 0; });
  console.log("  " + s + ": 可见 " + out[s].menus.length + " 项");
  console.log("     相对 ADMIN 少: " + JSON.stringify(missing));
  if (extra.length) console.log("     相对 ADMIN 多: " + JSON.stringify(extra));
}
await browser.close();
