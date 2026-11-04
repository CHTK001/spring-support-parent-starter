package com.chua.starter.datasourcehub.support.model;

/**
 * 通用标签（key-value），用于 SkyWalking 服务的附加属性、端点日志等。
 *
 * @param key   键
 * @param value 值
 * @author CH
 * @since 4.0.0.51
 */
public record TagVO(String key, String value) {

    /**
     * 由原始标签创建（空字符串规范化）
     *
     * @param raw 原始标签
     * @return 标签
     */
    public static TagVO of(String key, String value) {
        return new TagVO(key == null ? "" : key, value == null ? "" : value);
    }
}
