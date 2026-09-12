package com.chua.common.support.math.unit.name;

/**
* 命名格式转换工具类，提供驼峰命名等多种命名格式转换功能
*
* @author CH
* @since 4.0.0
 */
public class NamingCase {

    /**
    * 私有构造函数，防止实例化
     */
    private NamingCase() {
    }

    /**
    * 将带分隔符的命名（如下划线、短横线、空格）转换为驼峰命名
    * <p>示例：toCamelCase("hello_world") 返回 "helloWorld"。</p>
    *
    * @param name 原始名称
    * @return 驼峰格式的名称
     */
    public static String toCamelCase(String name) {
        if (name == null || name.isEmpty()) {
            return name;
        }
        StringBuilder sb = new StringBuilder();
        boolean nextUpper = false;
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            if (c == '_' || c == '-' || c == ' ') {
                nextUpper = true;
            } else if (nextUpper) {
                sb.append(Character.toUpperCase(c));
                nextUpper = false;
            } else {
                if (sb.length() == 0) {
                    sb.append(Character.toLowerCase(c));
                } else {
                    sb.append(c);
                }
            }
        }
        return sb.toString();
    }

    /**
    * 将字符串的首字母转换为大写
    * <p>示例：toFirstUpperCase("hello") 返回 "Hello"。</p>
    *
    * @param name 原始字符串
    * @return 首字母大写后的字符串
     */
    public static String toFirstUpperCase(String name) {
        if (name == null || name.isEmpty()) {
            return name;
        }
        return Character.toUpperCase(name.charAt(0)) + name.substring(1);
    }
}
