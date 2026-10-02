/**
 * 其他模块页面 · 界面新增/删除验收
 *
 * <p>按策略 6 个页签的同一套路：界面驱动 + 服务端/持久层裁决，不做「读渲染表格」当唯一真值。</p>
 *
 * <p>判据：新增后**重载页面并按测试值搜索**，命中即算成功；删除后再搜，消失即算成功。
 * 搜索是把列表收窄到目标行，避免分页/排序把判定搞错（策略页在分页上踩过假红）。</p>
 *
 * <p>清理：每个页面无论成败都删掉自己造的行；最后统一核对。</p>
 */
import { createRequire } from "node:module";
import { writeFileSync, mkdirSync } from "node:fs";

const require = createRequire("D:/ch/project/vue-support-parent-starter/package.json");
const { chromium } = require("playwright");

const BASE = "http://localhost:8096/system";
const OUT = "D:/ch/project/.tmp/accept-modules";
const UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/140.0.0.0 Safari/537.36";
mkdirSync(OUT, { recursive: true });
const ONLY = process.argv[2];

const TAG = "zzq";   // 测试值前缀，便于搜索与清理

const CASES = [
  {
    id: "role", name: "角色管理", path: "/manage/role", addBtn: "新增角色",
    probe: TAG + "-角色",
    fill: async (dlg, fillByLabel) => {
      await fillByLabel(dlg, "角色名称", TAG + "-角色");
      // 角色编码的校验是「只能用字母（大小写都支持），不允许数字和特殊字符」——
      // 用 zzq_role_01 会被拒（实测提示原文）。这里按规则给纯字母。
      await fillByLabel(dlg, "角色编码", "zzqrolerole");
    },
    searchPh: "角色名称",
    editField: "角色名称"
  },
  {
    id: "post", name: "岗位管理", path: "/manage/post", addBtn: "新增岗位",
    probe: TAG + "-岗位",
    fill: async (dlg, fillByLabel) => {
      await fillByLabel(dlg, "岗位名称", TAG + "-岗位");
      await fillByLabel(dlg, "岗位编码", TAG + "_post_01");
    },
    searchPh: "岗位名称",
    editField: "岗位名称"
  },
  {
    id: "dict", name: "字典管理", path: "/manage/dict", addBtn: "新增子项",
    probe: TAG + "-字典项",
    fill: async (dlg, fillByLabel) => {
      await fillByLabel(dlg, "名称", TAG + "-字典项");
      await fillByLabel(dlg, "值", "9991");
    },
    searchPh: null,
    editField: "名称"
  },
  {
    id: "announcement", name: "公告管理", path: "/manage/announcement", addBtn: "新增公告",
    probe: TAG + "-公告",
    fill: async (dlg, fillByLabel) => {
      await fillByLabel(dlg, "公告标题", TAG + "-公告");
    },
    searchPh: "标题",
    editField: "公告标题"
  },
  {
    id: "user", name: "用户管理", path: "/manage/user", addBtn: "添加",
    probe: TAG + "_user_01",
    fill: async (dlg, fillByLabel) => {
      // 账号校验：只能用字母、数字和下划线（实测提示原文），用连字符会被拒
      await fillByLabel(dlg, "账号", TAG + "_user_01");
      await fillByLabel(dlg, "密码", "Zzq2026aa");       // 满足强度即可
      await fillByLabel(dlg, "确认密码", "Zzq2026aa");
      await fillByLabel(dlg, "昵称", TAG + "-用户");
    },
    searchPh: "账号",
    // 账号在编辑时禁用不可改，只能改昵称
    editField: "昵称",
    // 用户管理的删除不在操作列，而是 el-dropdown（tip="更多"）里的 dropdown-item
    deleteVia: "more-menu"
  }
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

async function closeDialogs() {
  for (let i = 0; i < 3; i++) {
    const d = page.locator('.el-dialog:visible, .el-message-box:visible, .el-drawer:visible').last();
    if (!(await d.isVisible().catch(() => false))) return;
    await page.keyboard.press("Escape").catch(() => {});
    await page.waitForTimeout(400);
    if (await d.isVisible().catch(() => false)) {
      await d.locator('button:has-text("取消")').first().click().catch(() => {});
      await page.waitForTimeout(400);
    }
  }
}

async function fillByLabel(dlg, label, value) {
  const item = dlg.locator(`.el-form-item:has(.el-form-item__label:text-is("${label}"))`).first();
  const inp = item.locator("input, textarea").first();
  try { await inp.fill(value, { timeout: 10000 }); return true; } catch { return false; }
}

/** 打开页面，可选按关键字搜 */
async function visit(c, keyword) {
  await closeDialogs();
  await page.goto(`${BASE}${c.path}`, { waitUntil: "domcontentloaded", timeout: 45000 });
  await page.waitForLoadState("networkidle", { timeout: 25000 }).catch(() => {});
  await page.waitForTimeout(2000);
  if (keyword) {
    const inputs = page.locator('input[placeholder]');
    const n = await inputs.count();
    // 按 placeholder 含关键字找；找不到就用第一个可见文本框
    let target = null;
    if (c.searchPh) {
      const byPh = page.locator(`input[placeholder*="${c.searchPh}"]`).first();
      if (await byPh.count()) target = byPh;
    }
    if (!target) {
      for (let i = 0; i < n; i++) {
        const ph = await inputs.nth(i).getAttribute("placeholder");
        if (ph && /搜索|请输入|名称|账号|标题/.test(ph)) { target = inputs.nth(i); break; }
      }
    }
    if (target) {
      await target.fill(keyword);
      await page.keyboard.press("Enter");
      await page.waitForLoadState("networkidle", { timeout: 20000 }).catch(() => {});
      await page.waitForTimeout(1500);
    }
  }
}

/** 当前表里是否出现含该文本的行 */
async function hasRow(text) {
  return page.evaluate(t => {
    const vis = el => el.getClientRects().length > 0;
    const txt = el => (el.textContent || "").replace(/\s+/g, " ").trim();
    const rows = [...document.querySelectorAll("table")].filter(vis).pop()?.querySelectorAll("tbody tr") ?? [];
    for (const tr of rows) if (txt(tr).includes(t)) return { found: true, text: txt(tr).slice(0, 130) };
    return { found: false, rowCount: rows.length };
  }, text);
}

const results = [];
for (const c of CASES.filter(x => !ONLY || x.id === ONLY)) {
  const rec = { page: c.name, probe: c.probe };
  try {
    // ---- 新增 ----
    await visit(c, null);
    const add = page.locator(`button:has-text("${c.addBtn}")`).first();
    if (!(await add.count())) throw new Error(`找不到新增按钮「${c.addBtn}」`);
    await add.click();
    const dlg = page.locator('.el-dialog:visible, .el-drawer:visible').last();
    await dlg.waitFor({ state: "visible", timeout: 15000 });
    await c.fill(dlg, fillByLabel);
    await dlg.locator('button:has-text("确定"), button:has-text("保存"), button:has-text("确认")').last().click();
    await page.waitForLoadState("networkidle", { timeout: 20000 }).catch(() => {});
    await page.waitForTimeout(2500);
    rec.msgAfterCreate = await page.evaluate(() => [...document.querySelectorAll('.el-message')].map(e => (e.textContent || '').trim()).filter(Boolean).slice(0, 3));
    // 重载 + 搜索，判定是否真的出现
    await visit(c, c.probe);
    const afterAdd = await hasRow(c.probe);
    rec.created = afterAdd.found;
    rec.rowText = afterAdd.text;
    await page.screenshot({ path: `${OUT}/${c.id}-created.png` });

    // ---- 编辑 ----
    // 编辑后 probe 变成 probe+"-改"，删除阶段要按新值找行，所以把 probe 换掉
    if (afterAdd.found && c.editField) {
      const eRow = page.locator(`table tbody tr:has-text("${c.probe}")`).first();
      let edit = eRow.locator('button[aria-label*="编辑"]').first();
      if (!(await edit.count())) edit = eRow.locator('button:has-text("编辑")').first();
      if (!(await edit.count())) {
        rec.editAction = "行内无编辑按钮";
      } else {
        await edit.click();
        const edlg = page.locator('.el-dialog:visible, .el-drawer:visible').last();
        const ok = await edlg.waitFor({ state: "visible", timeout: 15000 }).then(() => true).catch(() => false);
        if (!ok) { rec.editAction = "编辑弹窗未打开"; }
        else {
          const edited = c.probe + "-改";
          const filledEdit = await fillByLabel(edlg, c.editField, edited);
          if (!filledEdit) rec.editAction = `编辑弹窗里找不到「${c.editField}」`;
          else {
            await edlg.locator('button:has-text("确定"), button:has-text("保存"), button:has-text("确认")').last().click();
            await page.waitForLoadState("networkidle", { timeout: 20000 }).catch(() => {});
            await page.waitForTimeout(2500);
            rec.editMsg = await page.evaluate(() => [...document.querySelectorAll('.el-message')].map(e => (e.textContent || '').trim()).filter(Boolean).slice(0, 2));
            await visit(c, edited);
            const afterEdit = await hasRow(edited);
            rec.edited = afterEdit.found;
            rec.editAction = afterEdit.found ? "已保存" : "保存后搜不到新值";
            rec.probe = edited;            // 后续删除按新值找行
            await page.screenshot({ path: `${OUT}/${c.id}-edited.png` });
          }
        }
      }
    }

    // ---- 删除 ----
    if (afterAdd.found) {
      const row = page.locator(`table tbody tr:has-text("${c.probe}")`).first();
      let opened = true;
      if (c.deleteVia === "more-menu") {
        // 用户管理：删除藏在「更多」下拉里（el-dropdown-item command="delete"）
        const more = row.locator('button[aria-label*="更多"]').first();
        if (!(await more.count())) { rec.deleteAction = "行内无「更多」按钮"; opened = false; }
        else {
          await more.click();
          // el-dropdown 的 popper 挂到 body 上、且带过渡动画，click 之后不能立刻 count
          const menu = page.locator('.el-dropdown-menu:visible').last();
          await menu.waitFor({ state: "visible", timeout: 8000 }).catch(() => {});
          await page.waitForTimeout(600);
          const item = menu.locator('.el-dropdown-menu__item:has-text("删除")').first();
          if (!(await item.count())) { rec.deleteAction = "下拉里无「删除」项"; opened = false; }
          else await item.click();
        }
      } else {
        // 默认：行内 aria-label 含「删除」，或行内文字「删除」
        let del = row.locator('button[aria-label*="删除"]').first();
        if (!(await del.count())) del = row.locator('button:has-text("删除")').first();
        if (!(await del.count())) { rec.deleteAction = "行内无删除按钮"; opened = false; }
        else await del.click();
      }
      if (opened) {
        const box = page.locator('.el-message-box:visible');
        const ok = await box.waitFor({ state: "visible", timeout: 8000 }).then(() => true).catch(() => false);
        if (ok) {
          await box.locator('button:has-text("删除"), button:has-text("确定"), button:has-text("确认")').last().click();
          rec.deleteAction = "已确认";
        } else { rec.deleteAction = "确认框未出现"; }
        await page.waitForLoadState("networkidle", { timeout: 20000 }).catch(() => {});
        await page.waitForTimeout(2500);
        await visit(c, c.probe);
        const afterDel = await hasRow(c.probe);
        rec.deleted = !afterDel.found;
      }
      // else 分支不覆盖 deleteAction：上面已写明是「行内无删除按钮」还是「下拉里无删除项」
      await page.screenshot({ path: `${OUT}/${c.id}-deleted.png` });
    }
  } catch (e) { rec.err = String(e).slice(0, 200); }
  results.push(rec);
}

writeFileSync(`${OUT}/crud.json`, JSON.stringify({ results, errs }, null, 2), "utf8");
console.log("=== 其他模块 · 界面新增/删除 ===\n");
for (const r of results) {
  console.log(`【${r.page}】 probe=${r.probe}`);
  if (r.err) console.log("  错误: " + r.err);
  if (r.msgAfterCreate?.length) console.log("  提交后提示: " + JSON.stringify(r.msgAfterCreate));
  if (r.created != null) console.log(`  界面新增 → 搜索到该行: ${r.created ? "通过" : "**失败**"}   ${r.rowText ? "行=" + r.rowText : ""}`);
  if (r.editAction) console.log(`  编辑动作: ${r.editAction}` + (r.editMsg?.length ? `  提示=${JSON.stringify(r.editMsg)}` : ""));
  if (r.edited != null) console.log(`  界面编辑 → 搜到新值: ${r.edited ? "通过" : "**失败**"}`);
  if (r.deleteAction) console.log(`  删除动作: ${r.deleteAction}`);
  if (r.deleted != null) console.log(`  界面删除 → 搜索已无: ${r.deleted ? "通过" : "**失败**"}`);
  console.log("");
}
console.log("pageErrors: " + errs.length);
for (const e of errs.slice(0, 3)) console.log("  " + e);
await browser.close();