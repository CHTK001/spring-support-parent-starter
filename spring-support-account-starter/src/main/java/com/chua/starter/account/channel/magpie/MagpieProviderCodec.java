// [P3C A20 豁免] A20/A30/A31: 含日志/界面/SQL 字符串字面量等非业务魔法值场景，豁免。
package com.chua.starter.account.channel.magpie;

import com.chua.common.support.spi.annotations.Spi;
import com.chua.starter.common.support.channel.ChannelUpstreamCodec;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * magpie 上游 provider 的线上形态适配。
 *
 * <p>magpie 的列表接口 {@code GET /api/providers} 回的是「展示视图」，动作/保存接口
 * {@code /api/provider/*} 收的是「输入视图」，两者字段形态不同：</p>
 * <ul>
 *   <li>{@code balanceToken} 列表是 {@code {takes,set}} 对象，输入要求字符串；</li>
 *   <li>{@code models} 列表是对象数组 {@code [{id,name,...}]}，输入要求 string 数组；</li>
 *   <li>{@code key} 列表是 {@code ****abcd}，回传会让动作拿假 key 去测试，须置空。</li>
 * </ul>
 *
 * <p>这些是 magpie 私有的协议知识，全部收在本实现里；{@link MagpieUpstreamAdmin}
 * 只负责编排（取凭据、白名单、错误翻译），不再关心字段形态。</p>
 *
 * @author CH
 * @since 4.0.0.53
 */
@Spi(MagpieSupport.CHANNEL)
public class MagpieProviderCodec implements ChannelUpstreamCodec {

    /**
     * 请求体里由消费方消费、不属于 provider 字段的控制键。
     */
    private static final Set<String> CONTROL_KEYS = Set.of("connectionId", "label", "provider",
            "isNew", "new", "from");

    /**
     * 列表接口即时算出来的展示字段，写回没有意义。
     */
    private static final Set<String> DERIVED_KEYS = Set.of("keyList", "keyCount", "hasKey",
            "move", "account", "agents", "fetched", "error", "sponsored", "spent", "balance");

    /**
     * 凭据行里永远不该回传的键。
     */
    private static final Set<String> SECRET_KEYS = Set.of("key", "secret", "token", "apikey");

    @Override
    public String channel() {
        return MagpieSupport.CHANNEL;
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
    @Override
    public Map<String, Object> desensitize(Map<String, Object> provider) {
        Map<String, Object> safe = provider == null
                ? new LinkedHashMap<>() : new LinkedHashMap<>(provider);
        Object key = provider == null ? null : provider.get("key");
        boolean hasInlineKey = key instanceof String text && !text.isBlank();
        safe.put("key", hasInlineKey ? MagpieSupport.maskKey(String.valueOf(key)) : "");
        Object keyList = provider == null ? null : provider.get("keyList");
        int keyCount = keyList instanceof List<?> list ? list.size() : 0;
        if (keyList instanceof List<?> list) {
            safe.put("keyList", desensitizeKeyRows(list));
        }
        safe.put("keyCount", keyCount);
        safe.put("hasKey", hasInlineKey || keyCount > 0);
        // 私有凭据永远不回传（magpie 自身也不会下发，双保险）
        safe.remove("secretAccessKey");
        safe.remove("accessKeyID");
        return safe;
    }

    /**
     * 把前端回传的 provider 归一成 magpie 的输入结构。
     *
     * @param body 请求体（可含 {@code provider} 包装）
     * @return 可发送给 magpie 的 provider
     */
    @Override
    public Map<String, Object> toUpstreamInput(Map<String, Object> body) {
        Map<String, Object> payload = new LinkedHashMap<>();
        if (body == null) {
            return payload;
        }
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
        DERIVED_KEYS.forEach(payload::remove);
        normalizeBalanceToken(payload);
        normalizeModels(payload);
        clearMaskedKey(payload);
        return payload;
    }

    /**
     * 列表把余额凭据下发成 {@code {takes,set}} 说明对象，输入侧要求字符串。
     *
     * @param payload provider 字段
     */
    private static void normalizeBalanceToken(Map<String, Object> payload) {
        Object balanceToken = payload.get("balanceToken");
        if (balanceToken instanceof Map<?, ?> token) {
            Object value = token.get("token");
            payload.put("balanceToken", value == null ? "" : String.valueOf(value));
        } else if (balanceToken != null && !(balanceToken instanceof String)) {
            payload.put("balanceToken", String.valueOf(balanceToken));
        }
    }

    /**
     * 列表把模型下发成对象数组，输入侧要求 string 数组。
     *
     * @param payload provider 字段
     */
    private static void normalizeModels(Map<String, Object> payload) {
        Object models = payload.get("models");
        if (!(models instanceof List<?> items)) {
            return;
        }
        List<String> ids = new ArrayList<>(items.size());
        for (Object item : items) {
            if (item instanceof Map<?, ?> model) {
                Object id = model.get("id");
                if (id != null && !String.valueOf(id).isBlank()) {
                    ids.add(String.valueOf(id));
                }
            } else if (item != null && !String.valueOf(item).isBlank()) {
                ids.add(String.valueOf(item));
            }
        }
        payload.put("models", ids);
    }

    /**
     * 列表明文 key 已脱敏成 {@code ****abcd}，回传会让动作拿假凭据去测试。
     *
     * <p>留空 = 用 magpie 已保存的 key（save 也是同一语义）。</p>
     *
     * @param payload provider 字段
     */
    private static void clearMaskedKey(Map<String, Object> payload) {
        Object key = payload.get("key");
        if (key instanceof String text && text.startsWith("****")) {
            payload.put("key", "");
        }
    }

    /**
     * 脱敏 {@code keyList} 的每一行：只保留展示字段，去掉可能的明文。
     *
     * @param rows magpie 的 key 行
     * @return 脱敏后的行
     */
    private static List<Map<String, Object>> desensitizeKeyRows(List<?> rows) {
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
                if (SECRET_KEYS.contains(name.toLowerCase())) {
                    row.put(name, MagpieSupport.maskKey(v == null ? null : String.valueOf(v)));
                } else if (!"rest".equalsIgnoreCase(name)) {
                    row.put(name, v);
                }
            });
            safe.add(row);
        }
        return safe;
    }
}
