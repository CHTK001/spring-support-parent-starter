package com.chua.starter.common.support.lang;

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
    *
    * @param name 名称
    * @return 转为camel大小写的结果
     */
    public static String toCamelCase(String name) {
        if (name == null || name.isEmpty()) {
            return name;
        }
        String lower = name.toLowerCase();
        StringBuilder sb = new StringBuilder();
        boolean upperNext = false;
        for (char c : lower.toCharArray()) {
            if (c == '_' || c == '-' || c == ' ') {
                upperNext = true;
            } else {
                sb.append(upperNext ? Character.toUpperCase(c) : c);
                upperNext = false;
            }
        }
        return sb.toString();
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

