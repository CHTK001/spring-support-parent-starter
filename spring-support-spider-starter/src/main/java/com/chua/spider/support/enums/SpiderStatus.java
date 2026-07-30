package com.chua.spider.support.enums;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;

/**
 * 爬虫启停状态枚举。
 * <p>用于爬虫定义 {@code spider_status} 字段，表示爬虫的启用或禁用状态。
 *
 * @author CH
 * @since 4.0.0.42
 */
@Getter
@Schema(description = "爬虫启停状态")
public enum SpiderStatus {

    /**
     * 已启用
     */
    ENABLED(1, "已启用"),

    /**
     * 已禁用
     */
    DISABLED(0, "已禁用");

    /**
     * 数据库存储编码
     */
    private final int code;

    /**
     * 中文描述
     */
    private final String desc;

    /**
     * 构造枚举项。
     *
     * @param code 数据库存储编码
     * @param desc 中文描述
     */
    SpiderStatus(int code, String desc) {
        this.code = code;
        this.desc = desc;
    }
}