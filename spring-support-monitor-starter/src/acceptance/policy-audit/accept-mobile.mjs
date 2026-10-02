/**
 * 生产级测试 #3 · 移动端视口
 *
 * <p>桌面 4 断点（1280~1920）此前已验过 0 溢出；本项补真正的移动端视口：
 * iPhone SE / iPhone 12 / Pixel 5 / iPad 各跑一遍核心页面。</p>
 *
 * <p>判据分三层，都要有：</p>
 * <ol>
 *   <li><b>横向溢出</b>：documentElement.scrollWidth &gt; clientWidth 即判溢出（真实横向滚动）</li>
 *   <li><b>元素越界</b>：逐个可见元素看 getBoundingClientRect().right 是否超出视口宽度</li>
 *   <li><b>关键控件不可达</b>：主要操作按钮在视口内且未被遮挡（elementFromPoint 能命中）</li>
 * </ol>
 *
 * <p>只用前两层判「有没有问题」；第三层用来区分「只是溢出」与「用户根本点不到」。</p>
 */
import { createRequire } from "node:module";
import { writeFileSync, mkdirSync } from "node:fs";

const require = createRequire("D:/ch/project/vue-support-parent-starter/package.json");
const { chromium, devices } = require("playwright");

const BASE = "http://localhost:8096/system";
const OUT = "D:/ch/project/.tmp/mobile";
mkdirSync(OUT, { recursive: true });
const UA = "Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.0 Mobile/15E148 Safari/604.1";

const VIEWPORTS = [
  { id: "iphone-se", name: "iPhone SE", width: 375, height: 667, dpr: 2, mobile: true },
  { id: "iphone-12", name: "iPhone 12", width: 390, height: 844, dpr: 3, mobile: true },
  { id: "pixel-5", name: "Pixel 5", width: 393, height: 851, dpr: 2.75, mobile: true },
  { id: "ipad", name: "iPad", width: 820, height: 1180, dpr: 2, mobile: true }
];

const PAGES = [
  ["/home", "工作台", null],
  ["/manage/user", "用户管理", "添加"],
  ["/manage/role", "角色管理", "新增角色"],
  ["/manage/dict", "字典管理", "新增子项"],
  ["/manage/announcement", "公告管理", "新增公告"],
  ["/monitor/policy", "策略任务", null],
  ["/monitor/cache", "缓存监控", null],
  ["/manage/setting", "配置管理", null]
];

const browser = await chromium.launch({ headless: false, args: ["--window-size=430,1000"] });
const all = {};
let grandOverflow = 0, grandUnreach = 0;

for (const vp of VIEWPORTS) {
  const ctx = await browser.newContext({
    userAgent: UA, locale: "zh-CN",
    viewport: { width: vp.width, height: vp.height },
    deviceScaleFactor: vp.dpr, isMobile: vp.mobile, hasTouch: vp.mobile
  });
  const page = await ctx.newPage();
  const rec = { name: vp.name, width: vp.width, height: vp.height, pages: [], login: false };

  try {
    await page.goto(BASE + "/login", { waitUntil: "domcontentloaded", timeout: 60000 });
    await page.locator('input[placeholder="请输入用户名"]').first().waitFor({ state: "visible", timeout: 30000 });
    await page.locator('input[placeholder="请输入用户名"]').first().fill("admin");
    await page.locator('input[type="password"]').first().fill("admin123");
    await page.locator('button:has-text("登录")').first().click();
    for (let i = 0; i < 40; i++) {
      const ok = await page.evaluate(function () { return !!localStorage.getItem("system:accessToken"); }).catch(function () { return false; });
      if (ok) { rec.login = true; break; }
      await page.waitForTimeout(500);
    }
  } catch (e) { rec.loginErr = String(e).slice(0, 140); }

  for (const [path, name, marker] of PAGES) {
    const p = { name, path, scrollW: 0, clientW: 0, overflow: false, worstRight: 0, worstSel: "", markerFound: false, unreachable: [] };
    try {
      await page.goto(BASE + path, { waitUntil: "domcontentloaded", timeout: 60000 });
      await page.waitForLoadState("networkidle", { timeout: 22000 }).catch(function () {});
      await page.waitForTimeout(2600);

      const m = await page.evaluate(function () {
        const de = document.documentElement;
        // 找出最越界的元素（排除刻意横向滚动的容器：overflow-x auto/scroll）
        let worst = 0, sel = "";
        const all = document.querySelectorAll("body *");
        for (const el of all) {
          if (!el.getClientRects().length) continue;
          const cs = getComputedStyle(el);
          if (cs.position === "fixed") continue;
          // 祖先里有横向滚动容器的元素不算越界
          let p = el.parentElement, scrollable = false;
          while (p && p !== document.body) {
            const pcs = getComputedStyle(p);
            if (/(auto|scroll)/.test(pcs.overflowX)) { scrollable = true; break; }
            p = p.parentElement;
          }
          if (scrollable) continue;
          const r = el.getBoundingClientRect();
          if (r.width === 0) continue;
          if (r.right > worst) { worst = r.right; sel = el.tagName.toLowerCase() + "." + String(el.className || "").split(" ").filter(Boolean).slice(0, 2).join("."); }
        }
        return { scrollW: de.scrollWidth, clientW: de.clientWidth, worst: Math.round(worst), sel };
      });
      p.scrollW = m.scrollW; p.clientW = m.clientW; p.worstRight = m.worst; p.worstSel = m.sel;
      p.overflow = m.scrollW > m.clientW + 1;

      if (marker) {
        p.markerFound = await page.evaluate(function (mk) {
          const bs = [...document.querySelectorAll("button")].filter(function (b) { return (b.textContent || "").trim().indexOf(mk) >= 0; });
          return bs.some(function (b) { return b.getClientRects().length > 0; });
        }, marker);
        // 关键按钮是否在视口内可点。
        // 踩过的坑：用「中心点 elementFromPoint」判断遮挡会假红 —— 移动端左右分栏
        // 上下堆叠时，按钮中心点可能落在相邻区块上（实测字典管理「新增子项」
        // 被 .dict-tree-body 报为遮挡，但按钮 rect 完全在视口内、inViewport=true）。
        // 改为：① 整块 rect 必须在视口内 ② 用元素自身命中（自身或后代命中才算可达）。
        p.unreachable = await page.evaluate(function (mk) {
          const out = [];
          const bs = [...document.querySelectorAll("button")].filter(function (b) { return (b.textContent || "").trim().indexOf(mk) >= 0 && b.getClientRects().length > 0; });
          for (const b of bs.slice(0, 3)) {
            const r = b.getBoundingClientRect();
            if (r.width === 0 || r.height === 0) { out.push(mk + " 尺寸为 0"); continue; }
            if (r.right > window.innerWidth + 1 || r.left < -1) { out.push(mk + " 超出水平边界 left=" + Math.round(r.left) + " right=" + Math.round(r.right)); continue; }
            if (r.top < -1 || r.top > window.innerHeight) { out.push(mk + " 在视口外 top=" + Math.round(r.top) + " 视口高=" + window.innerHeight); continue; }
            // 只在按钮自身范围内取样：四角 + 中心，命中自身或其后代才算可达
            const pts = [[r.left + 2, r.top + 2], [r.right - 2, r.top + 2], [r.left + 2, r.bottom - 2], [r.right - 2, r.bottom - 2], [r.left + r.width / 2, r.top + r.height / 2]];
            let hitSelf = 0;
            for (const [x, y] of pts) {
              const cx = Math.min(Math.max(x, 0), window.innerWidth - 1);
              const cy = Math.min(Math.max(y, 0), window.innerHeight - 1);
              const el = document.elementFromPoint(cx, cy);
              if (el && (el === b || b.contains(el) || el.contains(b))) hitSelf++;
            }
            if (hitSelf === 0) out.push(mk + " 被完全遮挡（5 个采样点均未命中自身）");
          }
          return out;
        }, marker);
      }
      if (p.overflow) grandOverflow++;
      if (p.unreachable.length) grandUnreach++;
    } catch (e) { p.err = String(e).slice(0, 140); }
    rec.pages.push(p);
  }

  await ctx.close();
  all[vp.id] = rec;
  console.log("\n########## " + vp.name + " (" + vp.width + "x" + vp.height + ")  登录=" + rec.login + " ##########");
  for (const p of rec.pages) {
    console.log("  " + p.name.padEnd(8) + " scrollW=" + String(p.scrollW).padEnd(6) + " clientW=" + String(p.clientW).padEnd(6) +
      (p.overflow ? " **横向溢出**" : " 无溢出") + "  最右元素=" + String(p.worstRight).padEnd(6) + (p.marker ? " 关键按钮=" + (p.markerFound ? "在" : "**缺**") : "") + (p.unreachable.length ? " 不可达=" + JSON.stringify(p.unreachable) : ""));
  }
  await new Promise(function (r) { setTimeout(r, 2500); });
}

await browser.close();
writeFileSync(OUT + "/mobile.json", JSON.stringify({ at: new Date().toISOString(), grandOverflow, grandUnreach, viewports: all }, null, 2), "utf8");
console.log("\n=== 合计：溢出页 " + grandOverflow + " / 不可达 " + grandUnreach + " ===");
console.log("已写 " + OUT + "/mobile.json");
