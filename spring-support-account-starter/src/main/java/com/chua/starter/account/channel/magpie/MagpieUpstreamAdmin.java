// [P3C A20 豁免] A20/A30/A31: 含日志/界面/SQL 字符串字面量等非业务魔法值场景，豁免。
package com.chua.starter.account.channel.magpie;

import com.chua.common.support.spi.ServiceProvider;
import com.chua.common.support.spi.annotations.Spi;
import com.chua.starter.common.support.channel.ChannelException;
import com.chua.starter.common.support.channel.ChannelUpstreamAdmin;
import com.chua.starter.common.support.channel.ChannelUpstreamCodec;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Magpie 网关的上游 provider 管理实现。
 *
 * <p>渠道页的「上游」页签通过本实现增删改查网关里的 provider：读写走管理面
 * {@code /api/*}（页面 Cookie 鉴权），凭据一律脱敏后才回传前端。</p>
 *
 * <p><b>为什么要单独一层</b>：连接记录在 AI 模块，账号模块看不到；AI 的控制器只负责
 * 「按渠道 + 连接主键取出凭据」，具体的请求体形态、动作白名单、脱敏规则都属于
 * magpie 自己的知识，因此全部留在本模块。</p>
 *
 * <p>管理面默认不开（桌面版需在「设置 › 网络与共享 › 浏览器访问」打开），
 * 不可用时异常信息会给出可操作原因，不影响数据面（对话 / 模型 / 配额）。</p>
 *
 * @since 4.0.0.53
 * @author CH
 */
@Spi(MagpieSupport.CHANNEL)
public class MagpieUpstreamAdmin implements ChannelUpstreamAdmin {

    /**
     * 放行的只读动作：写操作必须走 {@link #save} / {@link #delete}。
     */
    private static final Set<String> ACTIONS = Set.of("detect", "models", "test");

    /**
     * 放行的插件动作：与 magpie {@code POST /api/plugins/{op}} 的 switch 一致。
     */
    private static final Set<String> PLUGIN_ACTIONS = Set.of("add", "remove", "update", "upgrade",
            "off", "options", "prefer", "registry", "mirror", "check");

    /**
     * 线上形态适配（SPI）。
     *
     * <p>列表脱敏、列表派生物清理、{@code balanceToken}/{@code models} 归一这些
     * magpie 私有协议知识都在 {@link MagpieProviderCodec} 里，本类只按渠道别名取用。</p>
     */
    private volatile ChannelUpstreamCodec codec;

    @Override
    public String channel() {
        return MagpieSupport.CHANNEL;
    }

    /**
     * 取本渠道的 provider 形态适配实现。
     *
     * @return 适配实现
     */
    private ChannelUpstreamCodec codec() {
        ChannelUpstreamCodec local = codec;
        if (local == null) {
            synchronized (this) {
                local = codec;
                if (local == null) {
                    local = ServiceProvider.of(ChannelUpstreamCodec.class).getExtension(channel());
                    if (local == null) {
                        throw new ChannelException(ChannelException.CODE_NOT_IMPLEMENTED,
                                "渠道 " + channel() + " 缺少 ChannelUpstreamCodec 实现");
                    }
                    codec = local;
                }
            }
        }
        return local;
    }

    /**
     * {@inheritDoc}
     *
     * <p>magpie 有插件机制（{@code /api/plugins*}），故插件页签可用。</p>
     */
    @Override
    public boolean supportsPlugins() {
        return true;
    }

    /**
     * {@inheritDoc}
     *
     * <p>magpie 内置登录与插件登录共用 {@code /api/signin/{id}} 轮询。</p>
     */
    @Override
    public boolean supportsSignin() {
        return true;
    }

    /**
     * {@inheritDoc}
     *
     * <p>只做地址归一化，不探活。</p>
     */
    @Override
    public Map<String, Object> describe(Map<String, Object> credentials) {
        Map<String, Object> out = new LinkedHashMap<>(4);
        out.put("baseUrl", MagpieSupport.baseUrl(credentials));
        out.put("pageUrl", MagpieSupport.pageUrl(credentials));
        return out;
    }

    /**
     * {@inheritDoc}
     *
     * <p>管理面 {@code GET /api/providers}；provider 的 {@code key} / {@code keyList}
     * 只留末四位，并给出 {@code keyCount} / {@code hasKey} 供前端判断是否已配置。</p>
     */
    @Override
    public Map<String, Object> list(Map<String, Object> credentials) {
        List<Map<String, Object>> providers = MagpieAdminClient.providers(credentials);
        List<Map<String, Object>> safe = new ArrayList<>(providers.size());
        ChannelUpstreamCodec codec = codec();
        for (Map<String, Object> provider : providers) {
            safe.add(codec.desensitize(provider));
        }
        Map<String, Object> out = new LinkedHashMap<>(2);
        out.put("providers", safe);
        return out;
    }

    /**
     * {@inheritDoc}
     *
     * <p>请求体形如 {@code { provider: {...}, isNew?, from? }}；{@code provider} 缺失时
     * 把其余字段当作 provider 字段。{@code isNew} 对应 magpie 的同级 {@code new:true}，
     * {@code from} 用于重命名（旧 id）。</p>
     */
    @Override
    public Map<String, Object> save(Map<String, Object> credentials, Map<String, Object> payload) {
        Map<String, Object> body = payload == null ? new LinkedHashMap<>() : payload;
        Map<String, Object> provider = codec().toUpstreamInput(body);
        if (Boolean.TRUE.equals(body.get("isNew")) || Boolean.TRUE.equals(body.get("new"))) {
            provider.put("new", true);
        }
        String from = asString(body.get("from"));
        if (from != null && !from.isBlank()) {
            provider.put("from", from.trim());
        }
        return MagpieAdminClient.save(credentials, provider);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Map<String, Object> delete(Map<String, Object> credentials, String providerId) {
        if (providerId == null || providerId.isBlank()) {
            throw new ChannelException(ChannelException.CODE_INVALID_PARAM, "必须指定要删除的 provider id");
        }
        return MagpieAdminClient.delete(credentials, providerId.trim());
    }

    /**
     * {@inheritDoc}
     *
     * <p>只放行 {@code detect} / {@code models} / {@code test}；{@code models} 的响应
     * 额外归一成 {@code modelIds}，前端不必再解一层对象。</p>
     */
    @Override
    public Map<String, Object> action(Map<String, Object> credentials, String action,
                                      Map<String, Object> payload) {
        String normalized = action == null ? "" : action.trim().toLowerCase(Locale.ROOT);
        if (!ACTIONS.contains(normalized)) {
            throw new ChannelException(ChannelException.CODE_INVALID_PARAM,
                    "不支持的 magpie provider 动作: " + action + "（仅 detect / models / test）");
        }
        Map<String, Object> body = payload == null ? new LinkedHashMap<>() : payload;
        Map<String, Object> result = MagpieAdminClient.action(credentials, normalized, codec().toUpstreamInput(body));
        if ("models".equals(normalized)) {
            result.put("modelIds", MagpieAdminClient.modelIds(result));
        }
        return result;
    }

    // ── 插件 / 登录 / 额度 ──

    /**
     * 插件清单（可选市场），并把 magpie 的字段归一成前端稳定形态。
     *
     * <p>{@code /api/plugins} 的返回里 {@code plugins[]} 直接内嵌包的 Entry，
     * 不同版本的键名不完全一致；这里统一成 {@code spec/name/off/version/latest/
     * providers/moved/error}，前端不再猜。</p>
     *
     * @param credentials 网关连接凭据
     * @param market      是否同时读市场（慢，可能访问 npm / GitHub）
     * @return {@code {plugins:[...], bun, bunVer, mirror, registry, listings}}
     */
    @Override
    public Map<String, Object> plugins(Map<String, Object> credentials, boolean market) {
        Map<String, Object> raw = MagpieAdminClient.plugins(credentials, market);
        Map<String, Object> state = asMap(raw.get("state"));
        Map<String, Object> out = new LinkedHashMap<>(8);
        out.put("plugins", normalizePlugins(state.get("plugins")));
        copyIfPresent(state, out, "bun", "bunVer", "mirror", "registry", "error");
        if (market) {
            Map<String, Object> marketState = asMap(raw.get("market"));
            out.put("listings", marketState.get("listings") == null ? List.of() : marketState.get("listings"));
            Object marketError = marketState.get("error");
            if (marketError != null) {
                out.put("marketError", marketError);
            }
        }
        return out;
    }

    /**
     * 插件动作（白名单），装 / 卸 / 升级后由调用方刷新路由。
     *
     * <p>{@code add} 首次会下载 Bun 再 npm 安装，magpie 自己给了 5 分钟超时；
     * 调用链必须允许多等，不能按普通接口的短超时处理。</p>
     *
     * @param credentials 网关连接凭据
     * @param op          动作名
     * @param payload     请求体
     * @return 上游响应
     */
    @Override
    public Map<String, Object> pluginAction(Map<String, Object> credentials, String op,
                                            Map<String, Object> payload) {
        String normalized = op == null ? "" : op.trim().toLowerCase(Locale.ROOT);
        if (!PLUGIN_ACTIONS.contains(normalized)) {
            throw new ChannelException(ChannelException.CODE_INVALID_PARAM,
                    "不支持的 magpie 插件动作: " + op + "（仅 add / remove / update / upgrade / off / "
                            + "options / prefer / registry / mirror / check）");
        }
        Map<String, Object> body = payload == null ? new LinkedHashMap<>() : new LinkedHashMap<>(payload);
        body.remove("connectionId");
        body.remove("label");
        return MagpieAdminClient.pluginAction(credentials, normalized, body);
    }

    /**
     * 发起上游登录：插件 provider 走 {@code /api/plugin-signin}，内置走 {@code /api/signin}。
     *
     * @param credentials 网关连接凭据
     * @param payload     {@code {plugin?, provider, method, inputs, key}} 或 {@code {agent, site, proxy, user}}
     * @return 登录会话
     */
    @Override
    public Map<String, Object> startSignin(Map<String, Object> credentials, Map<String, Object> payload) {
        Map<String, Object> body = payload == null ? new LinkedHashMap<>() : new LinkedHashMap<>(payload);
        body.remove("connectionId");
        body.remove("label");
        Object plugin = body.remove("plugin");
        boolean viaPlugin = Boolean.TRUE.equals(plugin) || body.get("provider") != null;
        return viaPlugin
                ? MagpieAdminClient.startPluginSignin(credentials, body)
                : MagpieAdminClient.startSignin(credentials, body);
    }

    /**
     * 查询登录会话（内置与插件共用一个轮询端点）。
     *
     * @param credentials 网关连接凭据
     * @param id          会话 id
     * @return 会话状态
     */
    @Override
    public Map<String, Object> signinStatus(Map<String, Object> credentials, String id) {
        if (id == null || id.isBlank()) {
            throw new ChannelException(ChannelException.CODE_INVALID_PARAM, "登录会话 id 缺失");
        }
        return MagpieAdminClient.signinStatus(credentials, id.trim());
    }

    /**
     * 取消登录会话。
     *
     * @param credentials 网关连接凭据
     * @param id          会话 id
     */
    @Override
    public void cancelSignin(Map<String, Object> credentials, String id) {
        if (id == null || id.isBlank()) {
            return;
        }
        MagpieAdminClient.cancelSignin(credentials, id.trim());
    }

    /**
     * 管理面额度卡（含签到状态与插件账号）。
     *
     * @param credentials 网关连接凭据
     * @param provider    provider id（可空）
     * @param user        账号（可空）
     * @return {@code {quotas:[...]}}
     */
    @Override
    public Map<String, Object> quotas(Map<String, Object> credentials, String provider, String user) {
        Map<String, Object> out = new LinkedHashMap<>(2);
        out.put("quotas", MagpieAdminClient.adminQuotas(credentials, provider, user));
        return out;
    }

    /**
     * 让 magpie 重新读一张额度卡。
     *
     * @param credentials 网关连接凭据
     * @param provider    provider id（必填）
     * @param user        账号（可空）
     * @return {@code {quotas:[...]}}
     */
    @Override
    public Map<String, Object> refreshQuota(Map<String, Object> credentials, String provider, String user) {
        if (provider == null || provider.isBlank()) {
            throw new ChannelException(ChannelException.CODE_INVALID_PARAM, "必须指定要刷新的 provider");
        }
        Map<String, Object> out = new LinkedHashMap<>(2);
        out.put("quotas", MagpieAdminClient.refreshQuota(credentials, provider.trim(), user));
        return out;
    }

    // ── 私有辅助 ──

    /**
     * 归一化插件行：magpie 不同版本键名不完全一致，这里统一成稳定字段。
     *
     * @param raw 原始列表
     * @return 归一化后的列表
     */
    private static List<Map<String, Object>> normalizePlugins(Object raw) {
        if (!(raw instanceof List<?> list)) {
            return List.of();
        }
        List<Map<String, Object>> out = new ArrayList<>(list.size());
        for (Object item : list) {
            if (!(item instanceof Map<?, ?> map)) {
                continue;
            }
            Map<String, Object> row = new LinkedHashMap<>(8);
            row.put("spec", firstOf(map, "spec", "Spec"));
            row.put("name", firstOf(map, "name", "Name", "ownName", "OwnName"));
            row.put("off", Boolean.TRUE.equals(firstOf(map, "off", "Off")));
            row.put("version", firstOf(map, "version", "Version"));
            row.put("latest", firstOf(map, "latest", "Latest"));
            Object providers = firstOf(map, "providers", "Providers");
            row.put("providers", providers == null ? List.of() : providers);
            Object moved = firstOf(map, "moved", "Moved");
            row.put("moved", moved == null ? List.of() : moved);
            row.put("error", firstOf(map, "error", "Error"));
            out.add(row);
        }
        return out;
    }

    /**
     * 取第一个非空的候选键。
     *
     * @param map 原始行
     * @param keys 候选键
     * @return 值；都没有时返回 {@code null}
     */
    private static Object firstOf(Map<?, ?> map, String... keys) {
        for (String key : keys) {
            Object value = map.get(key);
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    /**
     * 把 Map 转成字符串键副本（非 Map 返回空）。
     *
     * @param raw 原始值
     * @return 字符串键 Map
     */
    private static Map<String, Object> asMap(Object raw) {
        Map<String, Object> out = new LinkedHashMap<>();
        if (raw instanceof Map<?, ?> map) {
            map.forEach((k, v) -> {
                if (k != null) {
                    out.put(String.valueOf(k), v);
                }
            });
        }
        return out;
    }

    /**
     * 拷贝存在的键。
     *
     * @param source 源
     * @param target 目标
     * @param keys   键
     */
    private static void copyIfPresent(Map<String, Object> source, Map<String, Object> target, String... keys) {
        for (String key : keys) {
            if (source.get(key) != null) {
                target.put(key, source.get(key));
            }
        }
    }

    /**
     * 读取字符串字段。
     *
     * @param value 原始值
     * @return 字符串；非字符串返回 {@code null}
     */
    private static String asString(Object value) {
        return value instanceof String text ? text : null;
    }
}
