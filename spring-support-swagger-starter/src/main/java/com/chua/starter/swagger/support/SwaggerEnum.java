package com.chua.starter.swagger.support;

/**
 * Swagger枚举接口定义
 *
 * @author CH
 * @since 2025/10/14 13:29
 */
public interface SwaggerEnum {

    /**
     * 获取枚举编码
     *
     * @return 枚举编码，例如：1, "ACTIVE", "001" 等任意对象类型
     */
    Object getCode();

    /**
     * 获取枚举名称
     *
     * @return 枚举名称，例如："启用", "禁用", "待审核" 等字符串描述
     */
    String getName();
}
