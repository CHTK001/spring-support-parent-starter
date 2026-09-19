package com.chua.starter.monitor.support.model;

import lombok.Data;

/**
 * 环境变量条目视图。
 *
 * @author CH
 * @since 4.0.0.42
 */
@Data
public class EnvVarVO {

    /**
     * 变量名
     */
    private String name;

    /**
     * 变量值
     */
    private String value;

    /**
     * 构造一条环境变量记录。
     *
     * @param name  变量名
     * @param value 变量值
     * @return 填充完毕的视图对象
     */
    public static EnvVarVO of(String name, String value) {
        EnvVarVO vo = new EnvVarVO();
        vo.setName(name);
        vo.setValue(value);
        return vo;
    }
}
