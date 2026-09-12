package com.chua.common.support.math.unit.size;

/**
* 命名格式转换工具类
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
    * 将字符串的首字母转换为大写
    * <p>该工具类用于大小单位名称的格式转换场景。</p>
    *
    * @param name 原始字符串，允许为 空 或空
    * @return 首字母大写后的字符串，若入参为 空 或空则原样返回
     */
    public static String toFirstUpperCase(String name) {
        if (name == null || name.isEmpty()) {
            return name;
        }
        return Character.toUpperCase(name.charAt(0)) + name.substring(1);
    }
}
