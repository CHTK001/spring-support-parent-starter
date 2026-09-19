package com.chua.starter.monitor.support.model;

import lombok.Data;

/**
 * 服务行视图。
 *
 * <p>{@code state} 统一为大写的 RUNNING/STOPPED/STARTING/STOPPING，
 * {@code startType} 统一为 AUTOMATIC/MANUAL/DISABLED，避免前端按通道分支。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Data
public class ServiceRowVO {

    /**
     * 服务标识名
     */
    private String name;

    /**
     * 服务显示名，缺失时回落到标识名
     */
    private String displayName;

    /**
     * 运行状态（大写归一枚举）
     */
    private String state;

    /**
     * 启动类型（大写归一枚举）
     */
    private String startType;

    /**
     * 承载进程号，未运行时为 0
     */
    private long pid;
}
