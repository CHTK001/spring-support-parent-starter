package com.chua.report.client.starter.entity;

import lombok.Data;

/**
 * 配置值
 * @author CH
 * @since 2024/9/11
 */
@Data
public class ConfigValue {

    /**
     * 名称
     */
    private String name;
    /**
     * 值
     */
    private String value;

    /**
     * 环境
     */
    private String profileActive;
}
