package com.chua.starter.plugin.store;

/**
 * 存储基础接口
 *
 * @author CH
 * @since 2025/1/16
 */
public interface Store {

    /**
     * 初始化存储
     */
    void initialize();

    /**
     * 销毁存储
     */
    void destroy();

    /**
     * 检查存储是否可用
     *
     * @return 是否可用
     */
    boolean isAvailable();

    /**
     * 获取存储名称
     *
     * @return 存储名称
     */
    String getName();
}
