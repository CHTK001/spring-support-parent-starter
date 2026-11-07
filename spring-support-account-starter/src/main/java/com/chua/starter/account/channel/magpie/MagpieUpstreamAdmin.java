// [P3C A20 豁免] A20/A30/A31: 含日志/界面/SQL 字符串字面量等非业务魔法值场景，豁免。
package com.chua.starter.account.channel.magpie;

import com.chua.common.support.spi.annotations.Spi;
import com.chua.starter.common.support.channel.ChannelException;
import com.chua.starter.common.support.channel.ChannelUpstreamAdmin;

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
     * 请求体里由消费方消费、不属于 provider 字段的控制键。
     */
    private static final Set<String> CONTROL_KEYS = Set.of("connectionId", "label", "provider",
            "isNew", "new", "from");

    @Override
    public String channel() {
        return MagpieSupport.CHANNEL;
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
        for (Map<String, Object> provider : providers) {
            safe.add(sanitizeProvider(provider));
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
        Map<String, Object> provider = providerPayload(body);
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
        Map<String, Object> result = MagpieAdminClient.action(credentials, normalized, providerPayload(body));
        if ("models".equals(normalized)) {
            result.put("modelIds", MagpieAdminClient.modelIds(result));
        }
        return result;
    }

    // ── 私有辅助 ──

    /**
     * 从请求体里取出 provider 字段（剔除控制字段）。
     *
     * @param body 请求体
     * @return provider 字段副本
     */
    private static Map<String, Object> providerPayload(Map<String, Object> body) {
        Map<String, Object> payload = new LinkedHashMap<>();
        Object nested = body.get("provider");
        if (nested instanceof Map<?, ?> map) {
            map.forEach((k, v) -> {
                if (k != null) {
                    payload.put(String.valueOf(k), v);
                }
            });
        } else {
            payload.putAll(body);
        }
        CONTROL_KEYS.forEach(payload::remove);
        return payload;
    }

    /**
     * 上游 provider 脱敏：API key 只留末四位，并标记是否已配置。
     *
     * <p>magpie 的 {@code providerJSON} 不下发 {@code key}，凭据体现为 {@code keyList}
     * （每个 key 一行）。因此「有没有凭据」主要看 {@code keyList} 长度；同时把
     * key 行里可能出现的明文 key / secret 一律剥掉。</p>
     *
     * @param provider 上游原始字段
     * @return 脱敏后的副本
     */
    private static Map<String, Object> sanitizeProvider(Map<String, Object> provider) {
        Map<String, Object> safe = new LinkedHashMap<>(provider);
        Object key = provider.get("key");
        boolean hasInlineKey = key instanceof String text && !text.isBlank();
        if (hasInlineKey) {
            safe.put("key", MagpieSupport.maskKey(String.valueOf(key)));
        } else {
            safe.put("key", "");
        }
        Object keyList = provider.get("keyList");
        int keyCount = keyList instanceof List<?> list ? list.size() : 0;
        if (keyList instanceof List<?> list) {
            safe.put("keyList", sanitizeKeyRows(list));
        }
        safe.put("keyCount", keyCount);
        safe.put("hasKey", hasInlineKey || keyCount > 0);
        // 私有凭据永远不回传（magpie 自身也不会下发，双保险）
        safe.remove("secretAccessKey");
        safe.remove("accessKeyID");
        return safe;
    }

    /**
     * 脱敏 {@code keyList} 的每一行：只保留展示字段，去掉可能的明文。
     *
     * @param rows magpie 的 key 行
     * @return 脱敏后的行
     */
    private static List<Map<String, Object>> sanitizeKeyRows(List<?> rows) {
        List<Map<String, Object>> safe = new ArrayList<>(rows.size());
        for (Object item : rows) {
            if (!(item instanceof Map<?, ?> map)) {
                continue;
            }
            Map<String, Object> row = new LinkedHashMap<>(map.size());
            map.forEach((k, v) -> {
                if (k == null) {
                    return;
                }
                String name = String.valueOf(k);
                if ("key".equalsIgnoreCase(name) || "secret".equalsIgnoreCase(name)
                        || "token".equalsIgnoreCase(name) || "apiKey".equalsIgnoreCase(name)) {
                    row.put(name, MagpieSupport.maskKey(v == null ? null : String.valueOf(v)));
                } else if (!"rest".equalsIgnoreCase(name)) {
                    row.put(name, v);
                }
            });
            safe.add(row);
        }
        return safe;
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
