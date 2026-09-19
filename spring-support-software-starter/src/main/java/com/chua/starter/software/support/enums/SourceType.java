package com.chua.starter.software.support.enums;

/**
 * 软件源类型
 *
 * @since 2026/7/25
 * @author CH
 */
public enum SourceType {
    /**
     * 官方仓库
     *
     * @param 0 0
     * @return OFFICIAL的结果
     */
    OFFICIAL(0),
    /**
     * 第三方仓库
     *
     * @param 1 1
     * @return THIRD_PARTY的结果
     */
    THIRD_PARTY(1),
    /**
     * 自建源
     *
     * @param 2 2
     * @return SELF_BUILT的结果
     */
    SELF_BUILT(2),
    /**
     * 本地文件
     *
     * @param 3 3
     * @return 本地的结果
     */
    LOCAL(3);

    /** 值 */
    private final int value;

    /**
    * 源类型。
    * @param value 值
    */
    SourceType(int value) {
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
