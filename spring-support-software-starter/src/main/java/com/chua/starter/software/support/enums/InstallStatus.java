package com.chua.starter.software.support.enums;

/**
 * 安装状态
 *
 * @since 2026/7/25
 * @author CH
 */
public enum InstallStatus {
    /** 未安装 */
    NOT_INSTALLED(0, "未安装"),
    /** 安装中 */
    INSTALLING(1, "安装中"),
    /** 已安装 */
    INSTALLED(2, "已安装"),
    /** 安装失败 */
    FAILED(3, "安装失败"),
    /** 卸载中 */
    UNINSTALLING(4, "卸载中"),
    /** 已卸载 */
    UNINSTALLED(5, "已卸载"),
    /** 更新中 */
    UPDATING(6, "更新中"),
    /** 更新失败 */
    UPDATE_FAILED(7, "更新失败");

    /** 值 */
    private final int value;
    /** 名称 */
    private final String name;

    InstallStatus(int value, String name) {
        this.value = value;
        this.name = name;
    }

    public int getValue() {
        return value;
    }

    public String getName() {
        return name;
    }

    /**
     * 根据值获取枚举
     *
     * @param value 状态值
     * @return 对应枚举，如果找不到则返回null
     */
    public static InstallStatus fromValue(int value) {
        for (InstallStatus status : values()) {
            if (status.getValue() == value) {
                return status;
            }
        }
        return null;
    }
}
