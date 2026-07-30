package com.chua.starter.software.support.enums;

/**
 * 软件源类型
 *
 * @author CH
 * @since 2026/7/25
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

    private final int value;

    SourceType(int value) {
        this.value = value;
    }

    public int getValue() {
        return value;
    }
}
