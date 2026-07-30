package com.chua.starter.software.support.enums;

/**
 * 软件状态
 *
 * @author CH
 * @since 2026/7/25
 */
public enum SoftwareStatus {
    /** 正常 */
    NORMAL(0),
    /** 已删除 */
    DELETED(1),
    /** 已禁用 */
    DISABLED(2);

    private final int value;

    SoftwareStatus(int value) {
        this.value = value;
    }

    public int getValue() {
        return value;
    }
}
