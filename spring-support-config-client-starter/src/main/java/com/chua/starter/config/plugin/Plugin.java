package com.chua.starter.config.plugin;

import com.chua.starter.config.entity.KeyValue;

/**
 * 插件
 *
 * @author CH
 */
public interface Plugin<T> {
    /**
     * 监听
     *
     * @param keyValue 监听
     * @param response 响应
     */
    void onListener(KeyValue keyValue, T response);
}
