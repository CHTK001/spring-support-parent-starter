package com.chua.starter.ai.support.service;

import com.chua.starter.ai.support.entity.SysAiAppKey;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Function;

/**
 * AppKey 的模型范围解析：<b>所属分组的模型清单 ∩ Key 上的限定模型</b>。
 *
 * <p>为什么单独抽一个类：范围判定有两个入口，必须用同一份口径。</p>
 * <ul>
 *   <li>网关 {@code /v2/v1/*}：应用层在协议适配前拦越权；</li>
 *   <li>服务实例端口（OpenAI / Claude / Gemini 协议）：裸 HTTP 服务，不走 Spring 拦截器。</li>
 * </ul>
 * <p>两处若各写一份，迟早出现「网关拒绝、实例放行」的口子。</p>
 *
 * <p>返回值的三态语义（调用方务必区分）：</p>
 * <ul>
 *   <li>{@code null}：不限制（没绑分组、分组没配模型、Key 也没限定模型）；</li>
 *   <li>空集合：一个模型都不许（分组不存在 / 已停用）；</li>
 *   <li>非空集合：只许集合内的模型（小写比较，忽略大小写）。</li>
 * </ul>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
@Component
public class AppKeyModelScope {

    /**
     * 分组 → 模型清单的唯一来源（含分组停用判定）
     */
    private final GroupModelScopeProvider groupScopeProvider;

    /**
     * 创建模型范围解析器（生产装配：直接取对话门面的分组口径）。
     *
     * @param facade 对话门面，不能为 null
     */
    @Autowired
    public AppKeyModelScope(AiChatFacadeService facade) {
        this(facade::groupModelScope);
    }

    /**
     * 创建模型范围解析器（可注入任意分组口径，便于逐条覆盖判定规则）。
     *
     * @param groupScopeProvider 分组范围提供者，不能为 null
     */
    public AppKeyModelScope(GroupModelScopeProvider groupScopeProvider) {
        this.groupScopeProvider = groupScopeProvider;
    }

    /**
     * 解析 appKey 实体允许调用的模型范围。
     *
     * @param appKey appKey 实体，可为 null（此时视为不限制）
     * @return 允许的模型集合（小写）；{@code null} 表示不限制
     */
    public Set<String> resolve(SysAiAppKey appKey) {
        if (appKey == null) {
            return null;
        }
        return resolve(appKey.getGroupId(), appKey.getModel());
    }

    /**
     * 解析「分组范围 ∩ 限定模型」。
     *
     * @param groupId  appKey 绑定的分组标识，可为 null
     * @param modelCsv Key 上限定使用的模型（逗号分隔），可为 null
     * @return 允许的模型集合（小写）；{@code null} 表示不限制
     */
    public Set<String> resolve(Integer groupId, String modelCsv) {
        Set<String> groupScope = groupScope(groupId);
        Set<String> ownScope = parseCsv(modelCsv);
        if (groupScope == null) {
            return ownScope;
        }
        if (ownScope == null) {
            return groupScope;
        }
        Set<String> out = new LinkedHashSet<>();
        for (String code : groupScope) {
            if (contains(ownScope, code)) {
                out.add(code);
            }
        }
        return out;
    }

    /**
     * 分组可用的模型范围。
     *
     * @param groupId 分组标识，可为 null
     * @return 模型集合（小写）；{@code null} 表示该分组不限制模型
     */
    public Set<String> groupScope(Integer groupId) {
        if (groupId == null) {
            return null;
        }
        try {
            Set<String> scope = groupScopeProvider.groupModelScope(groupId);
            if (scope == null) {
                return null;
            }
            Set<String> normalized = new LinkedHashSet<>();
            for (String code : scope) {
                if (code != null && !code.isBlank()) {
                    normalized.add(code.trim().toLowerCase(Locale.ROOT));
                }
            }
            return normalized;
        } catch (Exception e) {
            log.warn("[AppKeyModelScope] 读取分组模型范围失败，按不限制处理: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 逗号分隔的模型编码 → 小写集合。
     *
     * @param raw 原始串，可为 null 或空白
     * @return 模型集合；未配置时返回 {@code null}
     */
    public static Set<String> parseCsv(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        Set<String> out = new LinkedHashSet<>();
        for (String part : raw.split(",")) {
            String v = part.trim();
            if (!v.isEmpty()) {
                out.add(v.toLowerCase(Locale.ROOT));
            }
        }
        return out.isEmpty() ? null : out;
    }

    /**
     * 忽略大小写判断集合里是否包含该模型（各方系统里模型编码大小写并不统一）。
     *
     * @param scope 允许的模型集合（小写），不能为 null
     * @param model 待判定的模型名，可为 null
     * @return 命中返回 true
     */
    public static boolean contains(Set<String> scope, String model) {
        if (model == null) {
            return false;
        }
        return scope.contains(model.trim().toLowerCase(Locale.ROOT));
    }

    /**
     * 范围校验：{@code scope} 为 null 时一律放行。
     *
     * @param scope 允许的模型集合，可为 null（不限制）
     * @param model 本次请求的模型名，可为 null
     * @return 允许返回 true
     */
    public static boolean allows(Set<String> scope, String model) {
        return scope == null || contains(scope, model);
    }
}
