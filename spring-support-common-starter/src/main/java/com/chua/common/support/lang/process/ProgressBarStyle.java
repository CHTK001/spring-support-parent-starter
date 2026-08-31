package com.chua.common.support.lang.process;

import java.util.HashMap;
import java.util.Map;

/**
 * 进度条样式枚举
 *
 * @author CH
 */
public enum ProgressBarStyle {

    /**
     * 彩色 Unicode 块样式
     */
    COLORFUL_UNICODE_BLOCK;

    private static final Map<String, ProgressBarStyle> lookup = new HashMap<>();

    static {
        for (ProgressBarStyle style : values()) {
            lookup.put(style.name(), style);
        }
    }

    /**
     * 根据名称获取样式
     *
     * @param name 样式名称
     * @return 对应的样式实例
     */
    public static ProgressBarStyle fromName(String name) {
        return lookup.getOrDefault(name, COLORFUL_UNICODE_BLOCK);
    }
}
