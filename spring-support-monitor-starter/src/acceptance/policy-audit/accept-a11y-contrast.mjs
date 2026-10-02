/**
 * 无障碍复核 · color-contrast 实测色值
 *
 * <p>axe 报的 color-contrast 命中侧栏 menu-ellipsis，但 axe 的判定基于它自己算出的
 * 前景/背景，遇到渐变、透明度、折叠态覆盖时容易算错。这里直接取 computed style 的
 * 实际 rgba，换算成相对亮度后自己算对比度，判断是否真的低于 WCAG AA 的 4.5:1
 * （大号文本 3:1）。</p>
 */
import { createRequire } from "node:module";
const require = createRequire("D:/ch/project/vue-support-parent-starter/package.json");
const { chromium } = require("playwright");

const BASE = "http://localhost:8096/system";
const UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/140.0.0.0 Safari/537.36";

const browser = await chromium.launch({ headless: false, args: ["--window-size=1680,1000"] });
const page = await (await browser.newContext({ userAgent: UA, locale: "zh-CN", viewport: { width: 1600, height: 950 } })).newPage();
await page.goto(BASE + "/login", { waitUntil: "domcontentloaded", timeout: 60000 });
await page.locator('input[placeholder="请输入用户名"]').first().waitFor({ state: "visible", timeout: 25000 });
await page.locator('input[placeholder="请输入用户名"]').first().fill("admin");
await page.locator('input[type="password"]').first().fill("admin123");
await page.locator('button:has-text("登录")').first().click();
await page.waitForTimeout(6000);
await page.goto(BASE + "/manage/user", { waitUntil: "domcontentloaded", timeout: 45000 });
await page.waitForLoadState("networkidle", { timeout: 20000 }).catch(function () {});
await page.waitForTimeout(3000);

const out = await page.evaluate(() => {
  function parse(c) {
    const m = String(c).match(/rgba?\(([^)]+)\)/);
    if (!m) return null;
    const p = m[1].split(",").map(function (x) { return parseFloat(x); });
    return { r: p[0], g: p[1], b: p[2], a: p.length > 3 ? p[3] : 1 };
  }
  // 元素的实际背景色：Element/本仓的 primary 按钮是 background-color: transparent
  // + background-image: linear-gradient(...) 上色的。只读 backgroundColor 会把
  // primary 按钮算成「白字白底 1:1」—— 那是采集缺陷不是产品缺陷（踩过一次）。
  // 这里把 background-image 里的首个 rgb()/color() 色标当作背景色。
  function effBg(el) {
    const cs = getComputedStyle(el);
    const bc = parse(cs.backgroundColor);
    const bi = cs.backgroundImage || "";
    if (bc && bc.a > 0.85) return bc;
    const m = bi.match(/rgba?\([^)]+\)/);
    if (m) return parse(m[0]);
    const m2 = bi.match(/color\(srgb ([\d.]+) ([\d.]+) ([\d.]+)/);
    if (m2) return { r: +m2[1] * 255, g: +m2[2] * 255, b: +m2[3] * 255, a: 1 };
    return null;
  }
  function lum(c) {
    function f(v) { v /= 255; return v <= 0.03928 ? v / 12.92 : Math.pow((v + 0.055) / 1.055, 2.4); }
    return 0.2126 * f(c.r) + 0.7152 * f(c.g) + 0.0722 * f(c.b);
  }
  // 逐层向上找到第一个不透明的背景（同样要合并 background-image）
  function bgOf(el) {
    let n = el;
    while (n && n !== document.documentElement) {
      const c = effBg(n);
      if (c && c.a > 0.85) return c;
      n = n.parentElement;
    }
    return { r: 255, g: 255, b: 255, a: 1 };
  }
  function ratio(a, b) {
    const l1 = lum(a), l2 = lum(b);
    return (Math.max(l1, l2) + 0.05) / (Math.min(l1, l2) + 0.05);
  }
  const targets = [...document.querySelectorAll(".menu-ellipsis, .layout-tabs-bar__item, .el-table th, .el-button, .el-form-item__label, a, .el-tag")]
    .filter(function (e) { return e.getClientRects().length > 0; })
    .slice(0, 26);
  return targets.map(function (el) {
    const cs = getComputedStyle(el);
    const fg = parse(cs.color);
    const bg = bgOf(el);
    if (!fg) return null;
    const size = parseFloat(cs.fontSize);
    const bold = parseInt(cs.fontWeight, 10) >= 700;
    const large = size >= 24 || (size >= 18.66 && bold);
    const r = ratio(fg, bg);
    return {
      text: (el.textContent || "").replace(/\s+/g, " ").trim().slice(0, 18),
      cls: (el.className || "").toString().slice(0, 42),
      fg: cs.color, bg: "rgb(" + Math.round(bg.r) + "," + Math.round(bg.g) + "," + Math.round(bg.b) + ")",
      size: size, ratio: Math.round(r * 100) / 100,
      need: large ? 3 : 4.5,
      pass: r >= (large ? 3 : 4.5)
    };
  }).filter(Boolean);
});

console.log("=== 实际色值与对比度（AA：普通文本 4.5:1，大号文本 3:1）===");
let fail = 0, pass = 0;
for (const r of out) {
  const mark = r.pass ? "✓" : "✗ 不达标";
  if (!r.pass) fail++; else pass++;
  console.log("  " + mark + "  " + String(r.ratio).padStart(6) + " (需 " + r.need + ")  " + r.size + "px  " + r.fg.padEnd(22) + " on " + r.bg + "  " + JSON.stringify(r.text));
}
console.log("\n  达标 " + pass + " / 不达标 " + fail + " / 共 " + out.length);
await browser.close();
