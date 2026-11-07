// [P3C A20 豁免] A20/A30/A31: 含日志/界面/SQL 字符串字面量等非业务魔法值场景，豁免。
package com.chua.starter.account.channel.magpie;

import com.chua.starter.common.support.channel.ChannelException;
import com.chua.starter.common.support.http.ChannelHttp;
import com.chua.starter.common.support.text.json.Json;

import java.net.URLEncoder;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Magpie 网关的 HTTP 客户端：数据面读数 + 管理面 provider / 签到。
 *
 * <p><b>两个面不是同一个地址</b>（详见 {@link MagpieSupport}）：</p>
 * <ul>
 *   <li>数据面 {@code :3425/v1/*}：模型清单、配额，凭 {@code Authorization: Bearer} 认证；</li>
 *   <li>管理面 {@code :3430/api/*}：provider 增删改查、签到、用量，凭<b>页面 Cookie</b> 认证。</li>
 * </ul>
 *
 * <p><b>管理面鉴权怎么做</b>（对照 magpie {@code internal/gui/web.go} 的 {@code webGuard}）：
 * 页面没有 Bearer 或 Key 头，只有两种放行方式——URL 上的 {@code ?k=<页面 Key>}，
 * 或它换来的 {@code magpie_web_<端口>} Cookie。因此本类先
 * {@code GET <页面地址>/?k=<页面 Key>}，从 {@code 303} 响应的 {@code Set-Cookie} 里取出
 * Cookie 并缓存，再带 Cookie 调 {@code /api/*}。</p>
 *
 * <p><b>稳定性</b>：{@code /api/*} 是 magpie 桌面页面 / Docker Web UI 的内部接口，
 * 不是官方稳定 API。所有管理面调用失败都由调用方降级（前端显示「管理面不可用」+ 原因），
 * 不影响渠道本身的对话能力。</p>
 *
 * <p>端点对照（magpie main 分支源码）：</p>
 * <ul>
 *   <li>{@code GET  /api/providers}：{@code {vendors:[], providers:[ProviderJSON]}}；</li>
 *   <li>{@code POST /api/provider/save}：body 为 Provider JSON，附 {@code new/from}；</li>
 *   <li>{@code POST /api/provider/delete}：body {@code {id}}；</li>
 *   <li>{@code POST /api/provider/{detect|models|test}}：分项探测；</li>
 *   <li>{@code POST /api/usage/{workbuddy|trae|minimax|qoder}-checkin}：一键签到；</li>
 *   <li>{@code GET  /v1/models}：数据面模型清单 {@code {data:[{id,...}]}}；</li>
 *   <li>{@code GET  /v1/magpie/quotas}：配额读数 {@code {data:[Quota...]}}。</li>
 * </ul>
 *
 * <p>数据面方法不吞异常：网络 / 非 2xx 抛 {@link ChannelException}，交调用方决定
 * 「报错」还是「降级为空」；管理面同理，但 401/403 会被换成可操作的中文提示。</p>
 *
 * @since 4.0.0.53
 * @author CH
 */
public final class MagpieAdminClient {

    /**
     * 页面 Cookie 的缓存有效期（毫秒）：30 分钟。
     *
     * <p>magpie 固定页面 Key 时 Cookie 可存活很久，但 {@code magpie web} 默认每次启动换新 Key，
     * 缓存太久只会拿到失效 Cookie，30 分钟是「少握手」与「跟得上重启」的折中。</p>
     */
    private static final long SESSION_TTL_MILLIS = 30 * 60 * 1000L;

    /**
     * 页面 Cookie 缓存：{@code 页面地址|页面 Key → Cookie}。
     */
    private static final Map<String, Session> SESSIONS = new ConcurrentHashMap<>();

    /**
     * 工具类，禁止实例化。
     */
    private MagpieAdminClient() {
    }

    // ==================== 管理面（页面） ====================

    /**
     * 判断管理面是否可用（{@code GET /api/providers} 能读通）。
     *
     * <p>不再用 {@code /api/hello} 做探活：那是 3425 上 Claude Desktop 兼容命名空间的端点，
     * 页面没开也能 200，用它判断会得出「可管理」的假象。</p>
     *
     * @param credentials 连接凭据
     * @return true 表示页面接口可用且 Key 有效
     */
    public static boolean manageable(Map<String, Object> credentials) {
        try {
            providers(credentials);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 管理面 provider 列表。
     *
     * @param credentials 连接凭据
     * @return provider 列表；无数据返回空列表
     */
    public static List<Map<String, Object>> providers(Map<String, Object> credentials) {
        String body = adminGet(credentials, MagpieSupport.PROVIDERS_PATH);
        Map<String, Object> payload = parseObject(body);
        Object raw = payload.get("providers");
        if (raw == null) {
            raw = payload.get("data");
        }
        return asMapList(raw);
    }

    /**
     * 保存（新增 / 覆盖 / 重命名）provider。
     *
     * @param credentials 连接凭据
     * @param body        provider 字段（{@code id/name/preset/key/chat/models/...}），
     *                    可附 {@code new=true}（新增）或 {@code from=<旧 id>}（重命名）
     * @return 上游响应（magpie 保存后回整份 {@code {providers:[...]}}）
     */
    public static Map<String, Object> save(Map<String, Object> credentials, Map<String, Object> body) {
        return action(credentials, "save", body);
    }

    /**
     * 删除 provider。
     *
     * @param credentials 连接凭据
     * @param providerId  要删除的 provider id
     * @return 上游响应
     */
    public static Map<String, Object> delete(Map<String, Object> credentials, String providerId) {
        Map<String, Object> body = new LinkedHashMap<>(2);
        body.put("id", providerId);
        return action(credentials, "delete", body);
    }

    /**
     * provider 分项动作：{@code detect}（探测端点）/ {@code models}（拉模型）/
     * {@code test}（试连通）/ {@code save} / {@code delete}。
     *
     * @param credentials 连接凭据
     * @param action      动作名
     * @param body        请求体，可为空
     * @return 上游响应
     */
    public static Map<String, Object> action(Map<String, Object> credentials,
                                             String action, Map<String, Object> body) {
        String payload = body == null || body.isEmpty() ? "{}" : Json.toJson(body);
        String text = adminPost(credentials, MagpieSupport.PROVIDER_ACTION_PATH + action, payload);
        return parseObject(text);
    }

    /**
     * 触发一个上游的每日签到（magpie 的固定端点）。
     *
     * <p>只覆盖 magpie 自己实现签到的那几家：WorkBuddy(CN)、Trae(CN)、MiniMax(CN)、Qoder。
     * magpie 还有 {@code /api/usage/plugin-checkin} 给插件渠道用，需要逐 provider 指定，
     * 本类不猜。</p>
     *
     * @param credentials 连接凭据
     * @param vendor      上游标识（{@code workbuddy} / {@code trae} / {@code minimax} / {@code qoder}）
     * @return magpie 返回的签到结果行；无账号时为空列表
     */
    public static List<Map<String, Object>> checkin(Map<String, Object> credentials, String vendor) {
        String text = adminPost(credentials, MagpieSupport.USAGE_PATH + vendor + "-checkin", null);
        return asMapList(parseAny(text));
    }

    /**
     * 读取模型，并把 magpie 的 {@code models:[{id,name}]} 归一成 {@code id} 列表。
     *
     * <p>{@code /api/provider/models} 的返回是
     * {@code {count, dropped, provider:{models:[{id,name}]}}}，模型是<b>对象</b>不是字符串，
     * 直接 {@code join} 会得到 {@code [object Object]}，故单独抽一层。</p>
     *
     * @param credentials 连接凭据
     * @param provider    要拉模型的 provider（含 {@code id}，可附草稿字段）
     * @return 模型 id 列表
     */
    public static List<String> providerModelIds(Map<String, Object> credentials, Map<String, Object> provider) {
        return modelIds(action(credentials, "models", provider));
    }

    /**
     * 从 {@code /api/provider/models} 的响应里取模型 id。
     *
     * <p>响应形如 {@code {count, dropped, provider:{models:[{id,name}]}}}，模型是对象；
     * 也兼容某些版本直接在顶层给 {@code models}。</p>
     *
     * @param result 动作响应（可为空）
     * @return 去重后的模型 id 列表
     */
    public static List<String> modelIds(Map<String, Object> result) {
        List<String> ids = new ArrayList<>();
        if (result == null) {
            return ids;
        }
        Object nested = result.get("provider");
        collectModelIds(nested instanceof Map<?, ?> map ? map.get("models") : null, ids);
        collectModelIds(result.get("models"), ids);
        return ids;
    }

    // ==================== 数据面 ====================

    /**
     * 数据面模型清单（{@code GET /v1/models}）。
     *
     * @param baseUrl    网关根地址
     * @param gatewayKey 网关 key（可空）
     * @return 模型列表，每项保留原字段；无数据返回空列表
     */
    public static List<Map<String, Object>> models(String baseUrl, String gatewayKey) {
        String body = ChannelHttp.getJson(MagpieSupport.normalize(baseUrl) + MagpieSupport.MODELS_PATH,
                MagpieSupport.dataHeaders(gatewayKey));
        Map<String, Object> payload = parseObject(body);
        Object raw = payload.get("data");
        if (raw == null) {
            raw = payload.get("models");
        }
        return asMapList(raw);
    }

    /**
     * 配额读数（{@code GET /v1/magpie/quotas}）。
     *
     * @param baseUrl    网关根地址
     * @param gatewayKey 网关 key（可空）
     * @return 配额行列表（字段见 magpie {@code provider.Quota}）；无数据返回空列表
     */
    public static List<Map<String, Object>> quotas(String baseUrl, String gatewayKey) {
        String body = ChannelHttp.getJson(MagpieSupport.normalize(baseUrl) + MagpieSupport.QUOTAS_PATH,
                MagpieSupport.dataHeaders(gatewayKey));
        Map<String, Object> payload = parseObject(body);
        return asMapList(payload.get("data"));
    }

    // ==================== 内部：页面鉴权 ====================

    /**
     * 管理面 GET。
     *
     * @param credentials 连接凭据
     * @param path        以 {@code /api/} 开头的路径
     * @return 响应体
     */
    private static String adminGet(Map<String, Object> credentials, String path) {
        String pageUrl = MagpieSupport.pageUrl(credentials);
        return callAdmin(() -> ChannelHttp.getJson(pageUrl + path, adminHeaders(credentials)));
    }

    /**
     * 管理面 POST（body 可为 null，表示无请求体）。
     *
     * @param credentials 连接凭据
     * @param path        以 {@code /api/} 开头的路径
     * @param payload     JSON 请求体
     * @return 响应体
     */
    private static String adminPost(Map<String, Object> credentials, String path, String payload) {
        String pageUrl = MagpieSupport.pageUrl(credentials);
        return callAdmin(() -> ChannelHttp.postJson(pageUrl + path, payload, adminHeaders(credentials)));
    }

    /**
     * 统一包装管理面调用的错误：401/403 换成「页面 Key 被拒」的可操作提示，
     * 连接失败补上「桌面版默认不开页面端口」的说明。
     *
     * @param call 实际调用
     * @return 响应体
     */
    private static String callAdmin(java.util.function.Supplier<String> call) {
        try {
            return call.get();
        } catch (ChannelException e) {
            String message = e.getMessage() == null ? "" : e.getMessage();
            if (message.contains("-> 401") || message.contains("-> 403")) {
                throw new ChannelException(ChannelException.CODE_UPSTREAM_ERROR,
                        MagpieSupport.PAGE_KEY_INVALID_HINT + "（" + ChannelHttp.truncate(message) + "）", e);
            }
            if (message.contains("HTTP 请求失败")) {
                throw new ChannelException(ChannelException.CODE_UPSTREAM_ERROR,
                        MagpieSupport.PAGE_DISABLED_HINT + "（" + ChannelHttp.truncate(message) + "）", e);
            }
            throw e;
        }
    }

    /**
     * 构造管理面请求头（Cookie 按需握手获取）。
     *
     * @param credentials 连接凭据
     * @return 请求头
     */
    private static Map<String, String> adminHeaders(Map<String, Object> credentials) {
        String pageUrl = MagpieSupport.pageUrl(credentials);
        String pageKey = MagpieSupport.pageKey(credentials);
        if (MagpieSupport.isBlank(pageKey)) {
            throw new ChannelException(ChannelException.CODE_INVALID_PARAM, MagpieSupport.PAGE_KEY_MISSING_HINT);
        }
        Map<String, String> headers = MagpieSupport.jsonHeaders();
        headers.put("Cookie", cookie(pageUrl, pageKey));
        return headers;
    }

    /**
     * 取页面 Cookie（命中缓存直接用）。
     *
     * @param pageUrl 页面地址
     * @param pageKey 页面 Key
     * @return 形如 {@code magpie_web_3430=<key>} 的 Cookie 对
     */
    private static String cookie(String pageUrl, String pageKey) {
        String cacheKey = pageUrl + "|" + pageKey;
        long now = System.currentTimeMillis();
        Session cached = SESSIONS.get(cacheKey);
        if (cached != null && now - cached.at() < SESSION_TTL_MILLIS) {
            return cached.cookie();
        }
        String cookie = signIn(pageUrl, pageKey);
        SESSIONS.put(cacheKey, new Session(cookie, now));
        return cookie;
    }

    /**
     * 用页面 Key 换 Cookie：{@code GET <页面地址>/?k=<key>} →
     * 从 303 响应的 {@code Set-Cookie: magpie_web_<端口>=<key>; ...} 取第一段。
     *
     * <p>拿不到 {@code Set-Cookie}（例如前置代理改写了响应）时，按 magpie 的命名规则
     * 直接拼 {@code magpie_web_<端口>=<key>}；真错了下一个请求会 401，提示仍然可读。</p>
     *
     * @param pageUrl 页面地址
     * @param pageKey 页面 Key
     * @return Cookie 对
     */
    private static String signIn(String pageUrl, String pageKey) {
        String url = pageUrl + "/?k=" + URLEncoder.encode(pageKey, StandardCharsets.UTF_8);
        HttpResponse<String> response = ChannelHttp.getRaw(url, MagpieSupport.jsonHeaders());
        for (String setCookie : response.headers().allValues("Set-Cookie")) {
            String pair = setCookie;
            int semi = pair.indexOf(';');
            if (semi >= 0) {
                pair = pair.substring(0, semi);
            }
            int eq = pair.indexOf('=');
            if (eq > 0 && pair.substring(0, eq).startsWith(MagpieSupport.COOKIE_PREFIX)) {
                return pair;
            }
        }
        return MagpieSupport.cookieName(pageUrl) + "=" + pageKey;
    }

    // ==================== 内部：解析 ====================

    /**
     * 解析 JSON（对象 / 数组都吃，失败返回 {@code null}）。
     *
     * @param text JSON 文本
     * @return 解析结果
     */
    private static Object parseAny(String text) {
        if (MagpieSupport.isBlank(text)) {
            return null;
        }
        try {
            // Object.class：Jackson 按 JSON 形态分别给出 Map / List，对象与数组都能吃
            return Json.fromJson(text.trim(), Object.class);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 解析 JSON 对象（失败返回空 Map）。
     *
     * @param text JSON 文本
     * @return 解析结果
     */
    private static Map<String, Object> parseObject(String text) {
        Object parsed = parseAny(text);
        if (parsed instanceof Map<?, ?> map) {
            return toStringKeyMap(map);
        }
        return new LinkedHashMap<>();
    }

    /**
     * 拷贝成 {@code Map<String,Object>}。
     *
     * @param map 原始 Map
     * @return 字符串键副本
     */
    private static Map<String, Object> toStringKeyMap(Map<?, ?> map) {
        Map<String, Object> row = new LinkedHashMap<>(map.size());
        map.forEach((k, v) -> {
            if (k != null) {
                row.put(String.valueOf(k), v);
            }
        });
        return row;
    }

    /**
     * 归一化“对象列表”（兼容 List / 单对象 / null）。
     *
     * @param raw 原始节点
     * @return 对象列表
     */
    private static List<Map<String, Object>> asMapList(Object raw) {
        List<Map<String, Object>> rows = new ArrayList<>();
        if (raw instanceof List<?> list) {
            for (Object item : list) {
                if (item instanceof Map<?, ?> map) {
                    rows.add(toStringKeyMap(map));
                }
            }
        } else if (raw instanceof Map<?, ?> map) {
            rows.add(toStringKeyMap(map));
        }
        return rows;
    }

    /**
     * 从可能的模型节点里收集 id（元素可为字符串或 {@code {id,name}}）。
     *
     * @param raw 原始节点
     * @param out 收集目标（去重）
     */
    private static void collectModelIds(Object raw, List<String> out) {
        if (!(raw instanceof List<?> list)) {
            return;
        }
        for (Object item : list) {
            String id = null;
            if (item instanceof String text) {
                id = text;
            } else if (item instanceof Map<?, ?> map && map.get("id") != null) {
                id = String.valueOf(map.get("id"));
            }
            if (id != null && !id.isBlank() && !out.contains(id.trim())) {
                out.add(id.trim());
            }
        }
    }

    /**
     * 页面 Cookie 缓存条目。
     *
     * @param cookie Cookie 对
     * @param at     获取时刻（毫秒）
     */
    private record Session(String cookie, long at) {
    }
}
