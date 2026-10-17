package com.chua.starter.monitor.support.model;

/**
 * 服务行视图。
 *
 * <p>{@code state} 统一为大写的 RUNNING/STOPPED/STARTING/STOPPING，
 * {@code startType} 统一为 AUTOMATIC/MANUAL/DISABLED，避免前端按通道分支。</p>
 *
 * @param name        服务标识名
 * @param displayName 服务显示名，缺失时回落到标识名
 * @param state       运行状态（大写归一枚举）
 * @param startType   启动类型（大写归一枚举）
 * @param pid         承载进程号，未运行时为 0
 * @author CH
 * @since 4.0.0.42
 */
public record ServiceRowVO(
        String name,
        String displayName,
        String state,
        String startType,
        long pid) {
}
