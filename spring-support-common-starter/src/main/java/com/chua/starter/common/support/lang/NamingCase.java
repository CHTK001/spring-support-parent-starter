package com.chua.starter.common.support.lang;

import com.chua.common.support.lang.bean.NamingStyle;

/**
 * 命名转换工具。
 * @author CH
 * @since 4.0.0
 */
public final class NamingCase {

    /** 创建 名称大小写 实例 */
    private NamingCase() {
    }

    /**
     * 转为camel大小写
     * <p>将下划线/连字符/空格分隔的命名统一归一为下划线后，委托 {@link NamingStyle#UNDERSCORE} 转换。</p>
     *
     * @param name 名称
     * @return 转为camel大小写的结果
     */
    public static String toCamelCase(String name) {
        if (name == null || name.isEmpty()) {
            return name;
        }
        String normalized = name.toLowerCase().replace('-', '_').replace(' ', '_');
        return NamingStyle.UNDERSCORE.toCamel(normalized);
    }

    /**
     * 转为第一个大写大小写
     *
     * @param name 名称
     * @return 转为第一个大写大小写的结果
     */
    public static String toFirstUpperCase(String name) {
        if (name == null || name.isEmpty()) {
            return name;
        }
        String camel = toCamelCase(name);
        return Character.toUpperCase(camel.charAt(0)) + camel.substring(1);
    }
}

