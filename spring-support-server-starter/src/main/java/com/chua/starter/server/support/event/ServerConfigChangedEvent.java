package com.chua.starter.server.support.event;

import lombok.Getter;

/**
* 服务器系统配置变更事件
* <p>
* 保存配置后发布，时间轮调度器监听并动态重排周期任务
* （如连接缓存健康检查间隔变化即时生效；在离线/采集为主机级配置，不在此列）。
* </p>
*
* @author CH
* @since 4.0.0.42
 */
@Getter
public class ServerConfigChangedEvent {

    /** 变更的配置键集合 */
    private final java.util.Set<String> keys;

    /**
    * 构造
    *
    * @param keys 变更的配置键
    */
    public ServerConfigChangedEvent(java.util.Set<String> keys) {
        this.keys = keys == null ? java.util.Collections.emptySet() : keys;
    }

    /**
    * 判断是否涉及指定前缀的配置（如 connection）
    *
    * @param prefix 前缀
    * @return 是否命中
    */
    public boolean containsPrefix(String prefix) {
        if (prefix == null) {
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
