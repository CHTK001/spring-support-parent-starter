package com.chua.starter.appsystem.support.constants;

/**
 * 业务系统模块常量
 *
 * @author CH
 * @since 4.0.0.42
 */
public final class AppSystemConstants {

    /**
     * 接口根路径
     * <p>
     * 不能用 {@code /ms/system}：该前缀已由 spring-support-monitor-starter 的
     * {@code SystemManageController} 承载（本机进程/服务/网络等操作系统级管理），占用会直接冲突。
     * </p>
     */
    public static final String API_PREFIX = "/ms/app-system";

    /**
     * 操作系统标签：Linux
    */
    public static final String TYPE_LINUX = "LINUX";

    /**
     * 操作系统标签：Windows
    */
    public static final String TYPE_WINDOWS = "WINDOWS";

    /**
     * 工具类禁止实例化
     */
    private AppSystemConstants() {
    }
}
