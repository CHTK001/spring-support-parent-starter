package com.chua.starter.server.support.event;

import java.util.Set;

/**
 * 服务器系统配置变更事件
 * <p>
 * 保存配置后发布，时间轮调度器监听并动态重排周期任务
 * （如连接缓存健康检查间隔变化即时生效；在离线/采集为主机级配置，不在此列）。
 * </p>
 *
 * @param keys 变更的配置键集合
 * @author CH
 * @since 4.0.0.42
 */
public record ServerConfigChangedEvent(Set<String> keys) {

    /**
     * 兼容既有调用方（调度器仍按 JavaBean 风格读取）
     *
     * @return 变更的配置键集合
     */
    public Set<String> getKeys() {
        return keys;
    }

    /**
     * 判断是否涉及指定前缀的配置（如 connection）
     *
     * @param prefix 前缀
     * @return 是否命中
     */
    public boolean containsPrefix(String prefix) {
        if (prefix == null || keys == null) {
            return false;
        }
        for (String key : keys) {
            if (key != null && key.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }
}
