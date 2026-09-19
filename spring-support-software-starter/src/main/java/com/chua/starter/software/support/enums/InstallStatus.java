package com.chua.starter.software.support.enums;

/**
 * 安装状态
 *
 * @since 2026/7/25
 * @author CH
 */
public enum InstallStatus {
    /**
     * 未安装
     *
     * @param 0 0
     * @param "未安装" "未安装"
     * @return NOT_INSTALLED的结果
     */
    NOT_INSTALLED(0, "未安装"),
    /**
     * 安装中
     *
     * @param 1 1
     * @param "安装中" "安装中"
     * @return INSTALLING的结果
     */
    INSTALLING(1, "安装中"),
    /**
     * 已安装
     *
     * @param 2 2
     * @param "已安装" "已安装"
     * @return INSTALLED的结果
     */
    INSTALLED(2, "已安装"),
    /**
     * 安装失败
     *
     * @param 3 3
     * @param "安装失败" "安装失败"
     * @return 失败的结果
     */
    FAILED(3, "安装失败"),
    /**
     * 卸载中
     *
     * @param 4 4
     * @param "卸载中" "卸载中"
     * @return UNINSTALLING的结果
     */
    UNINSTALLING(4, "卸载中"),
    /**
     * 已卸载
     *
     * @param 5 5
     * @param "已卸载" "已卸载"
     * @return UNINSTALLED的结果
     */
    UNINSTALLED(5, "已卸载"),
    /**
     * 更新中
     *
     * @param 6 6
     * @param "更新中" "更新中"
     * @return 更新的结果
     */
    UPDATING(6, "更新中"),
    /**
     * 更新失败
     *
     * @param 7 7
     * @param "更新失败" "更新失败"
     * @return 更新失败的结果
     */
    UPDATE_FAILED(7, "更新失败");

    /** 值 */
    private final int value;
    /** 名称 */
    private final String name;

    /**
     * install状态。
     * @param value 值
     * @param name 名称
     */
    InstallStatus(int value, String name) {
        this.value = value;
        this.name = name;
    }

    /**
     * 获取值
     *
     * @return 获取值的结果
     */
    public int getValue() {
        return value;
    }

    /**
     * 获取名称
     *
     * @return 获取名称的结果
     */
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
