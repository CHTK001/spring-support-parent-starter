package com.chua.starter.monitor.support.model;

/**
 * 环境变量条目视图。
 *
 * @param name  变量名
 * @param value 变量值
 * @author CH
 * @since 4.0.0.42
 */
public record EnvVarVO(
        String name,
        String value) {

    /**
     * 构造一条环境变量记录。
     *
     * @param name  变量名
     * @param value 变量值
     * @return 填充完毕的视图对象
     */
    public static EnvVarVO of(String name, String value) {
        return new EnvVarVO(name, value);
    }
}
