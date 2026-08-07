package com.chua.starter.ai.support.model;

import lombok.Data;

/**
 * AI 模块查询模型。
 *
 * @author CH
 * @since 4.0.0.42
 */
@Data
public class AiModuleQuery {

    /**
     * 当前页码
     */
    private Integer current;

    /**
     * 每页条数
     */
    private Integer size;

    /**
     * 模块类型
     */
    private String sysAiModuleType;

    /**
     * 模块厂商
     */
    private String sysAiModuleVendor;

    /**
     * 关键字（模糊搜索）
     */
    private String keyword;
}
