package com.chua.starter.ai.support.model.vo;

/**
 * 用量数据源（UsageParser 扩展点目录项）
 *
 * @param name    数据源名称，即解析器 {@code name()}
 * @param type    解析器实现类简名，便于定位注册来源
 * @param enabled 是否可用
 * @author CH
 * @since 4.0.0.42
 */
public record AiUsageParserVO(
        String name,
        String type,
        boolean enabled
) {
}
