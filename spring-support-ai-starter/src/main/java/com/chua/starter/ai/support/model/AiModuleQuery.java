package com.chua.starter.ai.support.model;

/**
 * AI 模块查询模型。
 *
 * @param current 当前页码
 * @param size 每页条数
 * @param sysAiModuleType 模块类型
 * @param sysAiModuleVendor 模块厂商
 * @param keyword 关键字（模糊搜索）
 *
 * @since 4.0.0.42
 * @author CH
 */
public record AiModuleQuery(
        Integer current,
        Integer size,
        String sysAiModuleType,
        String sysAiModuleVendor,
        String keyword
) {
}
