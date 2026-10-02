/**
 * 生产级测试 #5 · 无障碍（axe-core）
 *
 * <p>axe-core 装在隔离目录 .tmp/axe（仓库是 pnpm workspace，npm 装不进去；
 * 也不写进 package.json，避免污染 lockfile）。本脚本把 axe.min.js 注入页面后跑扫描。</p>
 *
 * <p>扫全部真实页面，按 impact 分级统计。已知会命中但**属于既定设计**的先记录、
 * 不在这一轮改：例如侧栏图标按钮的 aria-label 缺失（菜单管理 7 个 circle 按钮）。</p>
 */
import { createRequire } from "node:module";
import { readFileSync, writeFileSync, mkdirSync } from "node:fs";

const require = createRequire("D:/ch/project/vue-support-parent-starter/package.json");
const { chromium } = require("playwright");

const AXE = readFileSync("D:/ch/project/.tmp/axe/node_modules/axe-core/axe.min.js", "utf8");
const BASE = "http://localhost:8096/system";
const OUT = "D:/ch/project/.tmp/a11y";
mkdirSync(OUT, { recursive: true });
const UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/140.0.0.0 Safari/537.36";

const PAGES = [
  ["/manage/user", "用户管理"], ["/manage/role", "角色管理"], ["/manage/menu", "菜单管理"],
  ["/manage/dept", "机构管理"], ["/manage/post", "岗位管理"], ["/manage/dict", "字典管理"],
  ["/manage/setting", "配置管理"], ["/manage/message", "消息管理"], ["/manage/announcement", "公告管理"],
  ["/manage/feedback", "反馈管理"], ["/manage/filestorage", "文件管理"],
  ["/manage/log/login", "登录日志"], ["/manage/syslog", "系统日志"],
  ["/monitor/server", "监控大屏"], ["/monitor/redis", "Redis 监控"], ["/monitor/cache", "缓存监控"],
  ["/monitor/policy", "策略任务"], ["/job/index", "调度总览"], ["/job/console", "调度管理"],
  ["/job/log", "作业日志"], ["/job/remote", "远程节点"], ["/home", "工作台"]
];

const browser = await chromium.launch({ headless: false, args: ["--window-size=1680,1000"] });
const ctx = await browser.newContext({ userAgent: UA, locale: "zh-CN", viewport: { width: 1600, height: 950 } });
const page = await ctx.newPage();
await page.addInitScript(`window.__AXE__ = ${JSON.stringify(AXE)};`);

await page.goto(BASE + "/login", { waitUntil: "domcontentloaded", timeout: 60000 });
await page.locator('input[placeholder="请输入用户名"]').first().waitFor({ state: "visible", timeout: 25000 });
await page.locator('input[placeholder="请输入用户名"]').first().fill("admin");
await page.locator('input[type="password"]').first().fill("admin123");
await page.locator('button:has-text("登录")').first().click();
await page.waitForTimeout(6000);
await page.waitForLoadState("networkidle", { timeout: 25000 }).catch(function () {});

// axe 每次导航后都要重新注入（页面导航会清掉 window 上的东西）
async function ensureAxe() {
  const has = await page.evaluate(() => typeof window.axe !== "undefined");
  if (!has) {
    await page.evaluate((src) => { (0, eval)(src); }, AXE);
  }
}

const all = [];
let totalByImpact = { critical: 0, serious: 0, moderate: 0, minor: 0 };

for (const [path, name] of PAGES) {
  const rec = { name, path, violations: 0, byImpact: {}, rules: [] };
  try {
    await page.goto(BASE + path, { waitUntil: "domcontentloaded", timeout: 45000 });
    await page.waitForLoadState("networkidle", { timeout: 20000 }).catch(function () {});
    await page.waitForTimeout(2500);
    await ensureAxe();

    const res = await page.evaluate(async function () {
      // @ts-ignore
      const r = await window.axe.run(document, {
        resultTypes: ["violations"],
        runOnly: { type: "tag", values: ["wcag2a", "wcag2aa", "wcag21a", "wcag21aa", "best-practice"] }
      });
      return r.violations.map(function (v) {
        return {
          id: v.id,
          impact: v.impact,
          help: v.help,
          nodes: v.nodes.length,
          sample: v.nodes.slice(0, 2).map(function (n) { return n.html.slice(0, 130); })
        };
      });
    });

    rec.violations = res.length;
    for (const v of res) {
      const k = v.impact || "unknown";
      rec.byImpact[k] = (rec.byImpact[k] || 0) + v.nodes;
      if (totalByImpact[k] != null) totalByImpact[k] += v.nodes;
    }
    rec.rules = res;
  } catch (e) { rec.err = String(e).slice(0, 150); }
  all.push(rec);
  const imp = JSON.stringify(rec.byImpact);
  console.log("  " + name.padEnd(10) + " 违规规则=" + String(rec.violations).padEnd(3) + " 节点数=" + imp + (rec.err ? "  ERR " + rec.err : ""));
}

writeFileSync(OUT + "/axe-report.json", JSON.stringify({ at: new Date().toISOString(), totalByImpact, pages: all }, null, 2), "utf8");

console.log("\n=== 合计（按 impact 统计违规节点数）===");
for (const [k, v] of Object.entries(totalByImpact)) console.log("  " + k.padEnd(10) + v);

console.log("\n=== 出现过的规则（按节点数降序，前 18）===");
const agg = {};
for (const p of all) for (const r of p.rules || []) {
  if (!agg[r.id]) agg[r.id] = { id: r.id, impact: r.impact, help: r.help, nodes: 0, pages: [] };
  agg[r.id].nodes += r.nodes;
  agg[r.id].pages.push(p.name);
}
for (const r of Object.values(agg).sort(function (a, b) { return b.nodes - a.nodes; }).slice(0, 18)) {
  console.log("  [" + String(r.impact).padEnd(8) + "] " + r.id.padEnd(30) + " 节点=" + String(r.nodes).padEnd(5) + " 页面数=" + r.pages.length);
  console.log("      " + r.help);
}
await browser.close();
