package com.chua.starter.config.plugin;

import com.chua.starter.config.entity.KeyValue;

/**
 * 插件
 *
 * @author CH
 */
public interface Plugin {
    /**
     * 监听
     *
     * @param keyValue 监听
     */
    void onListener(KeyValue keyValue);
}
