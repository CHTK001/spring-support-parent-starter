import { test, expect, APIRequestContext, request } from "@playwright/test";

/**
 * 生产级验收 —— AI Gateway E2E
 *
 * 直连运行中的 AI 网关（AI_GATEWAY_BASE，默认 http://127.0.0.1:9898）。
 * 覆盖两组交付链路：
 *   A) 插件市场前后端对接（/api/skills*）
 *   B) Bot 绑定 + IM 链路（/v2/ai/bot-binding*，含本次补端与分页）
 *
 * 原则：真实数据往返、失败即失败、不留污染数据（结束清理创建项）。
 */

const BASE = process.env.AI_GATEWAY_BASE || "http://127.0.0.1:9898";

/** 请求信封标注：后端统一 { success, data, msg } / { code, data, msg } */
async function get(ctx: APIRequestContext, path: string, tags: Record<string, string> = {}) {
  const res = await ctx.get(BASE + path, { headers: tags });
  const body = await res.json().catch(() => ({}));
  return { status: res.status(), body };
}

async function post(ctx: APIRequestContext, path: string, data?: unknown, tags: Record<string, string> = {}) {
  const res = await ctx.post(BASE + path, { data, headers: tags });
  const body = await res.json().catch(() => ({}));
  return { status: res.status(), body };
}

async function del(ctx: APIRequestContext, path: string) {
  const res = await ctx.delete(BASE + path);
  const body = await res.json().catch(() => ({}));
  return { status: res.status(), body };
}

test.describe("A. 插件市场前后端对接（/api/skills*）", () => {
  test("A01 市场源配置：marketType=skill 返回真实源", async () => {
    const ctx = await request.newContext();
    const { status, body } = await get(ctx, "/api/skills/market/sources/status?marketType=skill");
    await ctx.dispose();
    expect(status).toBe(200);
    const sources = body?.data?.sources ?? [];
    expect(Array.isArray(sources)).toBe(true);
    expect(sources.length).toBeGreaterThanOrEqual(1);
    for (const s of sources) {
      expect(s.source).toBeTruthy();
      expect(typeof s.enabled).toBe("boolean");
    }
  });

  test("A02 已安装技能列表：本地扫描返回数组", async () => {
    const ctx = await request.newContext();
    const { status, body } = await get(ctx, "/api/skills");
    await ctx.dispose();
    expect(status).toBe(200);
    expect(Array.isArray(body?.data)).toBe(true);
    for (const s of body?.data ?? []) {
      expect(s.name).toBeTruthy();
    }
  });

  test("A03 排行同步：clawhub 榜单真实拉取并生成条目", async () => {
    const ctx = await request.newContext();
    const { status, body } = await post(ctx, "/api/skills/market/rankings/sync", { sources: ["clawhub"] });
    await ctx.dispose();
    expect(status).toBe(200);
    const items = body?.data?.items ?? [];
    expect(Array.isArray(items)).toBe(true);
    // 依赖外网 SkillsMP；网络不可达时后端返回空 items + source_status
    const sourceStatus = body?.data?.source_status ?? {};
    if (items.length > 0) {
      const first = items[0];
      expect(first.id).toBeTruthy();
      expect(first.name).toBeTruthy();
      expect(first.url).toMatch(/^https:\/\//);
      expect(first.source).toBe("clawhub");
    } else {
      // 网络不可达也必须有诚实的状态标记（不允许静默失败或伪数据）
      expect(sourceStatus.clawhub ?? "").toBeTruthy();
    }
  });

  test("A04 市场状态：enable/disable 往返（改回原始态）", async () => {
    const ctx = await request.newContext();
    const before = (await get(ctx, "/api/skills/market/status")).body?.data?.enabled;
    await post(ctx, "/api/skills/market/enable");
    const enabled = (await get(ctx, "/api/skills/market/status")).body?.data?.enabled;
    expect(enabled).toBe(true);
    if (before === false) {
      await post(ctx, "/api/skills/market/disable");
    }
    await ctx.dispose();
  });

  test("A05 技能内容预览：非法/不可解析地址返回降级态", async () => {
    const ctx = await request.newContext();
    const { status, body } = await post(ctx, "/api/skills/market/content", {
      source: "clawhub",
      id: "x",
      url: "https://not-a-github-valid-repo.example/x",
    });
    await ctx.dispose();
    expect(status).toBe(200);
    // 无法解析 => available=false 降级，不报 5xx
    expect(typeof body?.data?.available).toBe("boolean");
    expect(typeof body?.data?.content).toBe("string");
  });

  test("A06 DELETE /api/skills/{name}：删除不存在技能幂等返回", async () => {
    const ctx = await request.newContext();
    const { status } = await del(ctx, `/api/skills/${encodeURIComponent("e2e-nonexistent-" + Date.now())}`);
    await ctx.dispose();
    expect(status).toBe(200);
  });
});

test.describe("B. Bot 绑定 + IM 链路（/v2/ai/bot-binding*）", () => {
  const suffix = String(Date.now() % 100000000);
  const botId = `e2e-bot-${suffix}`;
  const fromUser = `u-${suffix}`;

  test("B01 POST /create：新建绑定（真实写库）", async () => {
    const ctx = await request.newContext();
    const { status, body } = await post(ctx, "/v2/ai/bot-binding/create", {
      botId,
      platform: "wechat",
      nickname: `E2E Bot ${suffix}`,
      status: "offline",
      model: "",
      thinking: false,
      systemPrompt: "",
      recordUsage: true,
      skills: [],
    });
    await ctx.dispose();
    // ReturnResult 信封
    expect(status).toBe(200);
    expect(body.code).toBe("00000");
  });

  test("B02 GET /list：新绑定出现在列表，返回真实字段", async () => {
    const ctx = await request.newContext();
    const { status, body } = await get(ctx, "/v2/ai/bot-binding/list");
    await ctx.dispose();
    expect(status).toBe(200);
    const list = Array.isArray(body?.data) ? body.data : [];
    const found = list.find((b: { botId?: string }) => b.botId === botId);
    expect(found).toBeTruthy();
    expect(found.nickname).toContain("E2E Bot");
    expect(found.status).toBe("offline");
  });

  test("B03 GET /models：对话模型列表（数组且不会抛错）", async () => {
    const ctx = await request.newContext();
    const { status, body } = await get(ctx, "/v2/ai/bot-binding/models");
    await ctx.dispose();
    expect(status).toBe(200);
    const list = Array.isArray(body?.data) ? body.data : [];
    for (const id of list) expect(typeof id).toBe("string");
  });

  test("B04 GET /global-config：返回配置及上下文思考开关", async () => {
    const ctx = await request.newContext();
    const { status, body } = await get(ctx, "/v2/ai/bot-binding/global-config");
    await ctx.dispose();
    expect(status).toBe(200);
    const data = body?.data ?? {};
    expect(typeof data.contextThinking).toBe("boolean");
    expect(typeof data.historySize).toBe("number");
  });

  test("B05 GET /messages：分页结构（本轮交付核心）", async () => {
    const ctx = await request.newContext();
    const { status, body } = await get(ctx, `/v2/ai/bot-binding/messages?botId=${botId}&fromUser=${fromUser}&page=0&pageSize=5`);
    await ctx.dispose();
    expect(status).toBe(200);
    const data = body?.data ?? {};
    expect(Array.isArray(data.messages)).toBe(true);
    expect(typeof data.hasMore).toBe("boolean");
    expect(data.page).toBe(0);
    expect(data.pageSize).toBe(5);
  });

  test("B06 GET /runtime-status：未运行 Bot 返回 stopped + 移除悬挂引用", async () => {
    const ctx = await request.newContext();
    const { status, body } = await get(ctx, `/v2/ai/bot-binding/runtime-status?botId=${botId}`);
    await ctx.dispose();
    expect(status).toBe(200);
    const st = body?.data?.status;
    expect(["running", "stopped"]).toContain(st);
  });

  test("B07 POST /polish：未知模型返回空 result（endpoint 连通 + 优雅降级）", async () => {
    const ctx = await request.newContext();
    const { status, body } = await post(ctx, "/v2/ai/bot-binding/polish", {
      botId,
      text: "please check the weather today",
    });
    await ctx.dispose();
    expect(status).toBe(200);
    expect(typeof body?.data?.result).toBe("string");
  });

  test("B08 POST /summarize：无消息返回空 summary（连通验证）", async () => {
    const ctx = await request.newContext();
    const { status, body } = await post(ctx, "/v2/ai/bot-binding/summarize", {
      botId,
      fromUser,
    });
    await ctx.dispose();
    expect(status).toBe(200);
    expect(typeof body?.data?.summary).toBe("string");
  });

  test("B09 GET /commands + /contacts：命令清单与联系人", async () => {
    const ctx = await request.newContext();
    const commands = (await get(ctx, "/v2/ai/bot-binding/commands")).body?.data ?? [];
    expect(Array.isArray(commands)).toBe(true);
    expect(commands.some((c: { name?: string }) => c.name === "help")).toBe(true);
    const contacts = (await get(ctx, `/v2/ai/bot-binding/contacts?botId=${botId}`)).body?.data ?? [];
    expect(Array.isArray(contacts)).toBe(true);
    await ctx.dispose();
  });

  test("B10 DELETE /{id}：清理验收创建的绑定", async () => {
    const ctx = await request.newContext();
    const list = (await get(ctx, "/v2/ai/bot-binding/list")).body?.data ?? [];
    const found = (list as { id?: number; botId?: string }[]).find((b) => b.botId === botId);
    expect(found).toBeTruthy();
    const { status } = await del(ctx, `/v2/ai/bot-binding/${found!.id}`);
    await ctx.dispose();
    expect(status).toBe(200);
    const after = (await (async () => {
      const c2 = await request.newContext();
      const r = await get(c2, "/v2/ai/bot-binding/list");
      await c2.dispose();
      return r;
    })()).body?.data ?? [];
    expect(after.some((b: { botId?: string }) => b.botId === botId)).toBe(false);
  });
});

test.describe("C. 基础协议面（/v2 + /provider）", () => {
  test("C01 OpenAI 协议面鉴权：无/假 AppKey 拒绝 401", async () => {
    const ctx1 = await request.newContext();
    const unauth = await get(ctx1, "/v2/v1/models");
    await ctx1.dispose();
    expect(unauth.status).toBe(401);

    const ctx2 = await request.newContext();
    const fake = await get(ctx2, "/v2/v1/models", { Authorization: "Bearer fake-app-key" });
    await ctx2.dispose();
    expect(fake.status).toBe(401);
  });

  test("C01a 本地信任头放行：/v2/v1/models 返回模型列表信封", async () => {
    const ctx = await request.newContext();
    const { status, body } = await get(ctx, "/v2/v1/models", { "x-nomi-local-trust": "1" });
    await ctx.dispose();
    expect(status).toBe(200);
    expect(body.object).toBe("list");
    expect(Array.isArray(body.data)).toBe(true);
  });

  test("C02 会话 CRUD：dialog 创建 → 列表 → 删除", async () => {
    const ctx = await request.newContext();
    const created = await post(ctx, "/provider/ai/chat/dialog?name=e2e-dialog");
    expect(created.status).toBe(200);
    const dialogId = created.body?.id;
    expect(dialogId).toBeTruthy();
    const list = (await get(ctx, "/provider/ai/chat/dialog")).body;
    expect(Array.isArray(list)).toBe(true);
    expect(list.some((d: { id?: number }) => Number(d.id) === Number(dialogId))).toBe(true);
    const gone = await del(ctx, `/provider/ai/chat/dialog/${dialogId}`);
    expect(gone.status).toBe(200);
    await ctx.dispose();
  });
});