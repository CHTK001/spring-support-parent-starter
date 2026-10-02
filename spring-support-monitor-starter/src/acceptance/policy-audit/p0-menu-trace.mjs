/**
 * P0 深挖 · admin 侧栏空菜单
 *
 * <p>已确认的事实：</p>
 * <ul>
 *   <li>admin 侧栏 DOM 4165 字节 / 11 个 menu 元素；普通用户 27116 字节 / 85 个</li>
 *   <li>清掉 localStorage["system:menu"] 并重载后仍 0 项，且该缓存被重新写成
 *       「26 条扁平数组、0 条带 children」</li>
 *   <li>普通用户 localStorage 里<strong>没有</strong> system:menu 这个键，却能渲染 17 项</li>
 *   <li>直达 URL 时 admin 页面完全正常（数据都在）</li>
 * </ul>
 *
 * <p>本脚本抓登录后的全部 XHR/fetch，找菜单数据究竟从哪个接口来、返回结构长什么样，
 * 以及 admin 与普通用户走的是不是同一条路径。</p>
 */
import { createRequire } from "node:module";
import { writeFileSync, mkdirSync } from "node:fs";

const require = createRequire("D:/ch/project/vue-support-parent-starter/package.json");
const { chromium } = require("playwright");

const BASE = "http://localhost:8096/system";
const OUT = "D:/ch/project/.tmp/perm-matrix";
mkdirSync(OUT, { recursive: true });
const UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/140.0.0.0 Safari/537.36";

const browser = await chromium.launch({ headless: false, args: ["--window-size=1680,1000"] });

async function trace(u, p) {
  const ctx = await browser.newContext({ userAgent: UA, locale: "zh-CN", viewport: { width: 1600, height: 950 } });
  const page = await ctx.newPage();
  const calls = [];
  page.on("response", async function (res) {
    const url = res.url();
    if (!/\/system\/api\//.test(url)) return;
    let body = null;
    try { body = await res.text(); } catch { /* ignore */ }
    calls.push({ url: url.replace(/^https?:\/\/[^/]+/, ""), status: res.status(), body: body });
  });

  await page.goto(BASE + "/login", { waitUntil: "domcontentloaded", timeout: 60000 });
  await page.locator('input[placeholder="请输入用户名"]').first().waitFor({ state: "visible", timeout: 25000 });
  await page.locator('input[placeholder="请输入用户名"]').first().fill(u);
  await page.locator('input[type="password"]').first().fill(p);
  await page.locator('button:has-text("登录")').first().click();
  await page.waitForTimeout(7000);
  await page.waitForLoadState("networkidle", { timeout: 25000 }).catch(function () {});
  await page.waitForTimeout(2500);

  const dom = await page.evaluate(function () {
    const a = document.querySelector("aside");
    const names = ["工作台", "用户管理", "角色管理", "菜单管理", "机构管理", "字典管理", "参数配置",
      "公告管理", "反馈管理", "岗位管理", "消息管理", "监控管理", "监控大屏", "Redis 监控",
      "缓存监控", "策略任务", "登录日志", "系统日志", "文件管理", "远程节点", "调度管理", "作业日志"];
    const inAside = a ? (a.innerText || "") : "";
    return {
      asideLen: a ? a.innerHTML.length : 0,
      visible: names.filter(function (n) { return inAside.indexOf(n) >= 0; }).length,
      lsKeys: Object.keys(localStorage)
    };
  });
  await ctx.close();
  return { user: u, calls: calls, dom: dom };
}

const adminTrace = await trace("admin", "admin123");
const userTrace = await trace("zzq_user_01", "Zzq2026aa");
writeFileSync(OUT + "/menu-trace-admin.json", JSON.stringify(adminTrace, null, 2), "utf8");
writeFileSync(OUT + "/menu-trace-user.json", JSON.stringify(userTrace, null, 2), "utf8");

for (const t of [adminTrace, userTrace]) {
  console.log("\n########## " + t.user + "  侧栏 " + t.dom.asideLen + " 字节 / 可见 " + t.dom.visible + " 项 ##########");
  console.log("  localStorage 键: " + JSON.stringify(t.dom.lsKeys));
  console.log("  登录后 XHR 数: " + t.calls.length);
  for (const c of t.calls) {
    const short = c.url.length > 96 ? c.url.slice(0, 96) + "..." : c.url;
    let shape = "";
    if (c.body) {
      try {
        const j = JSON.parse(c.body);
        const d = j && j.data;
        if (Array.isArray(d)) {
          const withKids = d.filter(function (x) { return x && x.children && x.children.length; }).length;
          shape = "  data=数组[" + d.length + "] 含children=" + withKids;
          if (d[0]) {
            const k = Object.keys(d[0]);
            shape += " 首条键=" + JSON.stringify(k.slice(0, 8));
          }
        } else if (d && typeof d === "object") {
          shape = "  data=对象 键=" + JSON.stringify(Object.keys(d).slice(0, 8));
        } else {
          shape = "  data=" + (d === null ? "null" : typeof d);
        }
      } catch { shape = "  (非 JSON)"; }
    }
    console.log("    " + String(c.status).padEnd(4) + short + shape);
  }
}
await browser.close();
