package com.chua.starter.software.support.enums;

/**
* 更新类型
*
* @since 2026/7/25
* @author CH
 */
public enum UpdateType {
    /**
    * 常规更新
    *
    * @param 0 0
    * @return REGULAR的结果
    */
    REGULAR(0),
    /**
    * 安全更新
    *
    * @param 1 1
    * @return 安全性的结果
    */
    SECURITY(1),
    /**
    * 功能更新
    *
    * @param 2 2
    * @return 特征的结果
    */
    FEATURE(2),
    /**
    * 紧急修复
    *
    * @param 3 3
    * @return HOTFIX的结果
    */
    HOTFIX(3);

    /** 值 */
    private final int value;

    /**
    * 更新类型。
    * @param value 值
    */
    UpdateType(int value) {
        this.value = value;
    }

    /**
    * 获取值
    *
    * @return 获取值的结果
    */
    public int getValue() {
        return value;
    }
}
