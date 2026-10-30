package com.chua.starter.monitor.support.enums;

import lombok.Getter;

/**
 * 服务动作枚举。
 *
 * <p>控制层只传动作，具体命令由平台实现拼装，避免 {@code "start"} 这类字符串
 * 在多个命令族里各自 switch 出不同口径。</p>
 *
 * @since 2026/9/22
 * @author CH
 */
@Getter
public enum ServiceAction {

    /**
     * 启动
     */
    START("start", "启动"),
    /**
     * 停止
     */
    STOP("stop", "停止"),
    /**
     * 重启
     */
    RESTART("restart", "重启");

    /**
     * 动作标识
     */
    private final String code;
    /**
     * 中文描述，用于回显文案
     */
    private final String desc;

    /**
     * 构造方法
     * @param code 动作标识
     * @param desc 中文描述
     * @return 返回值
    */
    ServiceAction(String code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    /**
     * 解析动作标识，非法值按重启处理。
     *
     * @param raw 动作标识，可为空
     * @return 动作枚举，永不为 null
     */
    public static ServiceAction of(String raw) {
        for (ServiceAction action : values()) {
            if (action.code.equalsIgnoreCase(raw == null ? null : raw.trim())) {
                return action;
            }
        }
        return RESTART;
    }

    /**
     * 动作生效后的期望状态。
     *
     * @return {@code STOPPED} 或 {@code RUNNING}
     */
    public String expectState() {
        return this == STOP ? "STOPPED" : "RUNNING";
    }
}
