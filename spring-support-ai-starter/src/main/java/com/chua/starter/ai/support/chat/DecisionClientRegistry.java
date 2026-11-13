package com.chua.starter.ai.support.chat;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.chua.common.support.ai.chat.DecisionsClient;
import com.chua.common.support.ai.chat.DecisionsSetting;
import com.chua.starter.ai.support.manage.mapper.SysAiProviderMapper;
import com.chua.starter.ai.support.manage.store.SysAiProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 决策能力入口（Spring Bean）。
 *
 * <p>System one / decisions 是「决策协议」，utils 侧的实现是
 * {@link DecisionsClient}（{@code POST /v1/systemone} 或 {@code /v1/decisions}），
 * 与对话用的 {@code ChatClient} 是两套接口。这里把数据库里的决策供应商装配成
 * 可注入的客户端，供会话配置与 Agent 工具按选择调用。</p>
 *
 * <p>用法：</p>
 * <pre>{@code
 *   DecisionsClient client = registry.client(providerId);
 *   DecisionsResult result = client.question(DecisionQuestion.choice(...)).decide(text);
 * }</pre>
 *
 * <p>客户端按 providerId 缓存；供应商配置变化后调用 {@link #invalidate(String)}。</p>
 *
 * @author CH
 * @since 4.0.0.52
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DecisionClientRegistry {

    /**
     * 供应商表：平台、地址、凭证、代理都在这一行上
     */
    private final SysAiProviderMapper providerMapper;

    /**
     * providerId → 决策客户端（懒创建，失败不缓存）
     */
    private final Map<String, DecisionsClient> cache = new ConcurrentHashMap<>(4);

    /**
     * 平台是否属于决策协议（System one / decisions）。
     *
     * @param platform 供应商平台标识
     * @return true=决策类供应商
     */
    public static boolean isDecisionPlatform(String platform) {
        if (platform == null) {
            return false;
        }
        String p = platform.toLowerCase(Locale.ROOT);
        return p.contains("system-one") || p.contains("systemone") || p.contains("decisions");
    }

    /**
     * 可选的决策供应商清单（启用 + 平台匹配），供会话配置下拉使用。
     *
     * @return 每行 provider_id / name / platform / model
     */
    public List<Map<String, Object>> candidates() {
        List<Map<String, Object>> out = new ArrayList<>(4);
        List<SysAiProvider> rows = providerMapper.selectList(
                Wrappers.<SysAiProvider>lambdaQuery()
                        .eq(SysAiProvider::getEnabled, true)
                        .orderByAsc(SysAiProvider::getSortOrder));
        for (SysAiProvider p : rows) {
            if (!isDecisionPlatform(p.getPlatform())) {
                continue;
            }
            Map<String, Object> row = new LinkedHashMap<>(4);
            row.put("provider_id", p.getProviderId());
            row.put("name", p.getName());
            row.put("platform", p.getPlatform());
            row.put("model", extraString(p, "decision_model"));
            out.add(row);
        }
        return out;
    }

    /**
     * 按供应商取决策客户端。
     *
     * @param providerId 供应商 ID
     * @return 客户端；未登记、停用或构建失败返回 {@code null}
     */
    public DecisionsClient client(String providerId) {
        if (providerId == null || providerId.isBlank()) {
            return null;
        }
        DecisionsClient cached = cache.get(providerId);
        if (cached != null) {
            return cached;
        }
        SysAiProvider p = providerMapper.selectById(providerId);
        if (p == null || Boolean.FALSE.equals(p.getEnabled())) {
            return null;
        }
        DecisionsSetting setting = DecisionsSetting.builder()
                .provider(p.getPlatform())
                .baseUrl(p.getBaseUrl())
                .appKey(firstKey(p.getCredentials()))
                .proxy(p.getProxy())
                .model(extraString(p, "decision_model"))
                .authScheme(p.getAuthScheme())
                .authHeader(extraString(p, "auth_header"))
                .authPrefix(extraString(p, "auth_prefix"))
                .build();
        DecisionsClient client = DecisionsClient.create(setting);
        if (client != null) {
            cache.put(providerId, client);
        } else {
            log.warn("[Decision] 供应商 {} 无法创建决策客户端（platform={} baseUrl={}）",
                    providerId, p.getPlatform(), p.getBaseUrl());
        }
        return client;
    }

    /**
     * 供应商配置变化后让缓存失效。
     *
     * @param providerId 供应商 ID
     */
    public void invalidate(String providerId) {
        cache.remove(providerId);
    }

    /**
     * 清空全部决策客户端缓存。
     */
    public void invalidateAll() {
        cache.clear();
    }

    /**
     * 读 extra 里的字符串配置。
     *
     * @param p   供应商
     * @param key 键名
     * @return 值；缺失返回 null
     */
    private static String extraString(SysAiProvider p, String key) {
        Map<String, Object> extra = p.getExtra();
        Object v = extra == null ? null : extra.get(key);
        return v == null ? null : String.valueOf(v);
    }

    /**
     * 取凭证里的第一个 Key（多 Key 数组优先，其次单 Key）。
     *
     * @param creds 凭证
     * @return Key；无则 null
     */
    private static String firstKey(Map<String, Object> creds) {
        if (creds == null) {
            return null;
        }
        Object multi = creds.get("api_keys");
        if (multi instanceof List<?> list) {
            for (Object o : list) {
                if (o != null && !o.toString().isBlank()) {
                    return o.toString();
                }
            }
        }
        Object single = creds.get("api_key");
        return single == null ? null : single.toString();
    }
}
