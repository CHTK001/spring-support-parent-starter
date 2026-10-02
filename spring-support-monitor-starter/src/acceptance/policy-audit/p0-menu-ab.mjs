/**
 * P0 · admin 侧栏空菜单 —— 严格 A/B
 *
 * <p>已确认：GET /v2/user/menu 返回**扁平数组**（admin 26 条，0 条带 children，
 * 靠 parentId 表达层级）。侧栏要的是树。</p>
 *
 * <p>待判定：admin 渲染 0 项、普通用户渲染 17 项，是「同一份扁平数据不同结果」，
 * 还是两边接口返回的**条目集合本身就不同**。必须 A/B 同一时刻、同一浏览器、同一脚本。</p>
 *
 * <p>上一轮踩过的坑：账号被提前删掉，login 返回 data=undefined 却仍继续采侧栏，
 * 得出 aside 0 字节的假数据。脚本开头会断言登录成功，否则直接退出。</p>
 */
import { createRequire } from "node:module";
import { writeFileSync, mkdirSync } from "node:fs";

const require = createRequire("D:/ch/project/vue-support-parent-starter/package.json");
const { chromium } = require("playwright");

const API = "http://192.168.50.198:18170/system/api";
const BASE = "http://localhost:8096/system";
const OUT = "D:/ch/project/.tmp/perm-matrix";
const UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/140.0.0.0 Safari/537.36";
mkdirSync(OUT, { recursive: true });

// ---- 重建测试账号（admin/admin123 是内置账号，不动）----
async function api(method, path, token, body) {
  const headers = { "User-Agent": UA };
  if (body !== undefined) headers["Content-Type"] = "application/json";
  if (token) headers["Authorization"] = "Bearer " + token;
  const res = await fetch(API + path, { method, headers, body: body === undefined ? undefined : JSON.stringify(body) });
  const t = await res.text();
  let j = null; try { j = JSON.parse(t); } catch { /* */ }
  return j;
}
const adminLogin = await api("POST", "/v2/user/login", null, { username: "admin", password: "admin123" });
if (!adminLogin || !adminLogin.data) { console.log("admin 登录失败，终止"); process.exit(1); }
const AT = adminLogin.data.accessToken;
for (const a of [{ u: "zzq_ab_user", n: "验收AB-普通", r: "4" }, { u: "zzq_ab_ops", n: "验收AB-运维", r: "3" }]) {
  const r = await api("POST", "/v2/user/save", AT, {
    sysUserUsername: a.u, sysUserNickname: a.n, sysUserPassword: "Zzq2026aa",
    sysUserStatus: 1, roleIds: [a.r], updateRole: true
  });
  console.log("建号 " + a.u + ": " + (r && r.success ? "成功" : "失败 " + (r && r.msg)));
}

const KNOWN = ["工作台", "用户管理", "角色管理", "菜单管理", "机构管理", "字典管理", "参数配置",
  "公告管理", "反馈管理", "岗位管理", "消息管理", "监控管理", "监控大屏", "Redis 监控",
  "缓存监控", "策略任务", "登录日志", "系统日志", "文件管理", "远程节点", "调度管理", "作业日志"];

const browser = await chromium.launch({ headless: false, args: ["--window-size=1680,1000"] });
const out = {};

for (const s of [{ id: "ADMIN", u: "admin", p: "admin123" }, { id: "USER", u: "zzq_ab_user", p: "Zzq2026aa" }, { id: "OPS", u: "zzq_ab_ops", p: "Zzq2026aa" }]) {
  const ctx = await browser.newContext({ userAgent: UA, locale: "zh-CN", viewport: { width: 1600, height: 950 } });
  const page = await ctx.newPage();
  const rec = { id: s.id, loginOk: false, menuApi: null, aside: null };
  let menuBody = null;
  page.on("response", async function (res) {
    if (res.url().indexOf("/v2/user/menu") >= 0) { try { menuBody = await res.text(); } catch { /* */ } }
  });
  try {
    await page.goto(BASE + "/login", { waitUntil: "domcontentloaded", timeout: 60000 });
    await page.locator('input[placeholder="请输入用户名"]').first().waitFor({ state: "visible", timeout: 25000 });
    await page.locator('input[placeholder="请输入用户名"]').first().fill(s.u);
    await page.locator('input[type="password"]').first().fill(s.p);
    await page.locator('button:has-text("登录")').first().click();
    await page.waitForTimeout(7000);
    await page.waitForLoadState("networkidle", { timeout: 25000 }).catch(function () {});
    await page.waitForTimeout(2500);

    // **断言登录成功**，否则不采侧栏（上一轮就是漏了这步，采出 0 字节假数据）
    rec.loginOk = await page.evaluate(function () { return !!localStorage.getItem("system:accessToken"); });
    if (!rec.loginOk) { rec.err = "未登录成功（有 accessToken 才继续）"; await ctx.close(); out[s.id] = rec; continue; }

    if (menuBody) {
      try {
        const j = JSON.parse(menuBody);
        const arr = Array.isArray(j.data) ? j.data : (j.data && (j.data.list || j.data.menus)) || [];
        rec.menuApi = {
          条数: arr.length,
          含children: arr.filter(function (x) { return x && x.children && x.children.length; }).length,
          根节点: arr.filter(function (x) { return !x.parentId && x.parentId !== "0"; }).map(function (x) { return x.menuName || x.sysMenuTitle; }),
          parentId取值: [...new Set(arr.map(function (x) { return String(x.parentId); }))].slice(0, 12),
          名称列表: arr.map(function (x) { return x.menuName || x.sysMenuTitle; })
        };
      } catch (e) { rec.menuApi = { parseErr: String(e).slice(0, 80) }; }
    }

    rec.aside = await page.evaluate(function (known) {
      const a = document.querySelector("aside");
      if (!a) return { exists: false };
      const inAside = a.innerText || "";
      // 逐层看菜单 DOM：有没有 el-menu / 子菜单 / 折叠态
      const menus = [...a.querySelectorAll(".el-menu, .el-sub-menu, .el-menu-item")];
      return {
        exists: true,
        htmlLen: a.innerHTML.length,
        text: inAside.replace(/\s+/g, " ").trim(),
        visible: known.filter(function (n) { return inAside.indexOf(n) >= 0; }),
        elMenu: a.querySelectorAll(".el-menu").length,
        elSubMenu: a.querySelectorAll(".el-sub-menu").length,
        elMenuItem: a.querySelectorAll(".el-menu-item").length,
        折叠的子菜单: [...a.querySelectorAll(".el-sub-menu")].filter(function (e) { return e.className.indexOf("is-opened") < 0; }).map(function (e) { return (e.querySelector(".el-sub-menu__title") || {}).innerText || ""; }).filter(Boolean),
        asideCls: a.className
      };
    }, KNOWN);
    await page.screenshot({ path: OUT + "/p0-" + s.id + ".png" });
  } catch (e) { rec.err = String(e).slice(0, 160); }
  await ctx.close();
  out[s.id] = rec;

  console.log("\n########## " + s.id + " (" + s.u + ") 登录成功=" + rec.loginOk + " ##########");
  if (rec.err) console.log("  " + rec.err);
  if (rec.menuApi) {
    console.log("  /v2/user/menu 条数=" + rec.menuApi.条数 + " 含children=" + rec.menuApi.含children);
    console.log("    parentId 取值集合: " + JSON.stringify(rec.menuApi.parentId取值));
    console.log("    名称: " + JSON.stringify(rec.menuApi.名称列表));
  } else console.log("  /v2/user/menu 未捕获到响应");
  if (rec.aside && rec.aside.exists) {
    console.log("  aside: " + rec.aside.htmlLen + " 字节  elMenu=" + rec.aside.elMenu + " elSubMenu=" + rec.aside.elSubMenu + " elMenuItem=" + rec.aside.elMenuItem);
    console.log("    可见菜单(" + rec.aside.visible.length + "): " + JSON.stringify(rec.aside.visible));
    console.log("    折叠中的子菜单: " + JSON.stringify(rec.aside.折叠的子菜单));
    console.log("    aside 文本: " + JSON.stringify(rec.aside.text.slice(0, 200)));
  } else if (rec.aside) console.log("  aside 不存在");
}

writeFileSync(OUT + "/p0-ab.json", JSON.stringify(out, null, 2), "utf8");
await browser.close();
