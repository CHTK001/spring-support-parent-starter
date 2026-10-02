/**
 * 剩余 5 个管理页 · 结构深挖
 *
 * <p>上一轮发现它们的「新增」入口不是标准 `button:has-text("新增")`：
 * 部门管理在隐藏的 dock 里、菜单管理要先选中节点、配置管理是卡片分组、
 * 消息管理文案是「发送消息」、反馈管理本来就没有新增（用户提交、管理端只处理）。</p>
 *
 * <p>所以这一轮只做「把真实可点的入口列出来」，不做判定 ——
 * 判定要等拿到结构后按页写。</p>
 */
import { createRequire } from "node:module";
import { writeFileSync, mkdirSync } from "node:fs";

const require = createRequire("D:/ch/project/vue-support-parent-starter/package.json");
const { chromium } = require("playwright");

const BASE = "http://localhost:8096/system";
const OUT = "D:/ch/project/.tmp/accept-modules";
const UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/140.0.0.0 Safari/537.36";
mkdirSync(OUT, { recursive: true });

const PAGES = [
  { p: "/manage/dept", name: "部门管理" },
  { p: "/manage/menu", name: "菜单管理" },
  { p: "/manage/setting", name: "配置管理" },
  { p: "/manage/message", name: "消息管理" },
  { p: "/manage/feedback", name: "反馈管理" }
];

const browser = await chromium.launch({ headless: false, args: ["--window-size=1680,1000"] });
const page = await (await browser.newContext({ userAgent: UA, locale: "zh-CN", viewport: { width: 1600, height: 950 } })).newPage();
const errs = [];
page.on("pageerror", e => errs.push(String(e).slice(0, 150)));

await page.goto(`${BASE}/login`, { waitUntil: "domcontentloaded", timeout: 60000 });
await page.locator('input[placeholder="请输入用户名"]').first().waitFor({ state: "visible", timeout: 25000 });
await page.locator('input[placeholder="请输入用户名"]').first().fill("admin");
await page.locator('input[type="password"]').first().fill("admin123");
await page.locator('button:has-text("登录")').first().click();
await page.waitForFunction(() => !document.body.innerText.includes("欢迎登录"), null, { timeout: 60000 });

const report = {};
for (const pg of PAGES) {
  const rec = { name: pg.name };
  try {
    await page.goto(`${BASE}${pg.p}`, { waitUntil: "domcontentloaded", timeout: 45000 });
    await page.waitForLoadState("networkidle", { timeout: 25000 }).catch(() => {});
    await page.waitForTimeout(2500);

    rec.visibleButtons = await page.evaluate(() => {
      const vis = e => e.getClientRects().length > 0;
      return [...document.querySelectorAll("button")]
        .filter(vis)
        .map(x => ({ t: (x.textContent || "").replace(/\s+/g, " ").trim().slice(0, 16), al: x.getAttribute("aria-label") }))
        .filter(x => x.t || x.al);
    });
    // aria-label 型按钮（ReButton tip）常常没有文字内容，单列一遍
    rec.tipButtons = await page.evaluate(() => {
      const vis = e => e.getClientRects().length > 0;
      return [...document.querySelectorAll("button[aria-label]")].filter(vis).map(x => x.getAttribute("aria-label"));
    });
    rec.tables = await page.evaluate(() => {
      const vis = e => e.getClientRects().length > 0;
      return [...document.querySelectorAll("table")].filter(vis).map(t => ({
        rows: t.querySelectorAll("tbody tr").length,
        heads: [...t.querySelectorAll("thead th")].map(x => (x.textContent || "").replace(/\s+/g, " ").trim()).slice(0, 12)
      }));
    });
    // 树 / 卡片分组
    rec.trees = await page.evaluate(() => {
      const vis = e => e.getClientRects().length > 0;
      const ts = [...document.querySelectorAll(".el-tree, .el-table__row--level-0, .re-tree")].filter(vis);
      return ts.length ? ts.length : 0;
    });
    rec.treeNodeCount = await page.evaluate(() => document.querySelectorAll(".el-tree-node").length);
    rec.cards = await page.evaluate(() => {
      const vis = e => e.getClientRects().length > 0;
      return [...document.querySelectorAll(".el-card, [class*='group-card'], [class*='setting-card']")].filter(vis).length;
    });
    rec.tabs = await page.evaluate(() => [...document.querySelectorAll('.el-tabs__item, [role="tab"]')].map(x => (x.textContent || "").replace(/\s+/g, " ").trim()).filter(Boolean).slice(0, 10));
    rec.firstRowText = await page.evaluate(() => {
      const vis = e => e.getClientRects().length > 0;
      const t = [...document.querySelectorAll("table")].filter(vis).pop();
      const tr = t?.querySelector("tbody tr");
      return tr ? (tr.textContent || "").replace(/\s+/g, " ").trim().slice(0, 160) : null;
    });
    await page.screenshot({ path: `${OUT}/deep-${pg.p.replace(/\//g, "_")}.png`, fullPage: false });
  } catch (e) { rec.err = String(e).slice(0, 160); }
  report[pg.p] = rec;
}

writeFileSync(`${OUT}/deep.json`, JSON.stringify({ report, errs }, null, 2), "utf8");
for (const [k, v] of Object.entries(report)) {
  console.log(`\n########## ${v.name} ${k} ##########`);
  if (v.err) { console.log("  错误: " + v.err); continue; }
  console.log("  可见按钮: " + JSON.stringify([...new Set((v.visibleButtons || []).map(x => x.t || `[${x.al}]`))]));
  console.log("  tip 按钮: " + JSON.stringify([...new Set(v.tipButtons || [])]));
  console.log("  表格: " + JSON.stringify(v.tables));
  console.log(`  树节点=${v.treeNodeCount} 卡片=${v.cards} 标签页=${JSON.stringify(v.tabs)}`);
  if (v.firstRowText) console.log("  首行: " + v.firstRowText);
}
console.log("\npageErrors: " + errs.length);
await browser.close();
