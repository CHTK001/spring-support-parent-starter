package com.chua.starter.software.support.enums;

/**
 * 更新类型
 *
 * @author CH
 * @since 2026/7/25
 */
public enum UpdateType {
    /** 常规更新 */
    REGULAR(0),
    /** 安全更新 */
    SECURITY(1),
    /** 功能更新 */
    FEATURE(2),
    /** 紧急修复 */
    HOTFIX(3);

    private final int value;

    UpdateType(int value) {
        this.value = value;
    }

    public int getValue() {
        return value;
    }
}
