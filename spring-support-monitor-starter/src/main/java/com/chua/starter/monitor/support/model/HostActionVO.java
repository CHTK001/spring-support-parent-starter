package com.chua.starter.monitor.support.model;

/**
 * 主机动作结果（关闭/重启进程、启停服务、应用网络配置）。
 *
 * <p>动作一律是「下发平台命令 + 回查确认」，成功与否由通道回查决定，不能凭命令有回显推断，
 * 因此结果用本对象承载并由通道给出；控制层只负责转成对外响应体。</p>
 *
 * @param success 动作是否已确认生效
 * @param message 面向操作者的结果说明，失败时含目标机返回的原因
 * @param newPid  重启进程后的新进程号，其余动作为 null
 * @author CH
 * @since 4.0.0.42
 */
public record HostActionVO(boolean success, String message, Integer newPid) {

    /**
     * 已确认生效的动作结果。
     *
     * @param message 结果说明
     * @return 动作结果
     */
    public static HostActionVO ok(String message) {
        return new HostActionVO(true, message, null);
    }

    /**
     * 已确认生效并拉起新进程的动作结果。
     *
     * @param message 结果说明
     * @param newPid  新进程号，可为 null
     * @return 动作结果
     */
    public static HostActionVO ok(String message, Integer newPid) {
        return new HostActionVO(true, message, newPid);
    }

    /**
     * 未生效的动作结果。
     *
     * @param message 失败说明，须带目标机返回的原因
     * @return 动作结果
     */
    public static HostActionVO fail(String message) {
        return new HostActionVO(false, message, null);
    }
}
