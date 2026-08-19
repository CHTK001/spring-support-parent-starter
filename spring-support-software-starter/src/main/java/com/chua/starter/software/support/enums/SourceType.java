package com.chua.starter.software.support.enums;

/**
 * 软件源类型
 *
 * @since 2026/7/25
 * @author CH
 */
public enum SourceType {
    /** 官方仓库 */
    OFFICIAL(0),
    /** 第三方仓库 */
    THIRD_PARTY(1),
    /** 自建源 */
    SELF_BUILT(2),
    /** 本地文件 */
    LOCAL(3);

    /** 值 */
    private final int value;

    SourceType(int value) {
        this.value = value;
    }

    /** 获取Value */
    public int getValue() {
        return value;
    }
}
