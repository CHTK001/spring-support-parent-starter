package com.chua.starter.software.support.enums;

/**
 * 软件状态
 *
 * @since 2026/7/25
 * @author CH
 */
public enum SoftwareStatus {
    /**
     * 正常
     *
     * @param 0 0
     * @return NORMAL的结果
     */
    NORMAL(0),
    /**
     * 已删除
     *
     * @param 1 1
     * @return 删除的结果
     */
    DELETED(1),
    /**
     * 已禁用
     *
     * @param 2 2
     * @return 已禁用的结果
     */
    DISABLED(2);

    /**
     * 值
    */
    private final int value;

    /**
     * software状态。
     * @param value 值
     */
    SoftwareStatus(int value) {
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
