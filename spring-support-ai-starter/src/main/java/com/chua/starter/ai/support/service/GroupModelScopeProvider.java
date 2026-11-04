package com.chua.starter.ai.support.service;

import java.util.Set;

/**
 * 分组模型范围提供者：{@link AppKeyModelScope} 唯一的对外依赖。
 *
 * <p>刻意收成单方法接口而不是直接依赖 {@code AiChatFacadeService}：模型范围的判定规则
 * （分组清单 ∩ Key 限定模型的九种组合）是这套系统里最容易被改坏的逻辑，必须能逐条覆盖测试。
 * 依赖整个对话门面的话，一个单元测试要连带拉起几十个无关能力，测试最终会退化成「不敢写」。</p>
 *
 * @author CH
 * @since 4.0.0.52
 */
@FunctionalInterface
public interface GroupModelScopeProvider {

    /**
     * 查询分组配置的模型范围。
     *
     * @param groupId 分组标识，可为 null
     * @return 模型编码集合（大小写不敏感）；{@code null} 表示该分组不限制模型，
     *         空集合表示该分组一个模型都不给（分组不存在 / 已停用）
     */
    Set<String> groupModelScope(Integer groupId);
}
