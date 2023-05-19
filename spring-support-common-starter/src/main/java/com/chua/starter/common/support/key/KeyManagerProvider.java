package com.chua.starter.common.support.key;

/**
 * 密钥管理器
 * @author CH
 */
public interface KeyManagerProvider {
    /**
     * 获取key
     * @param binder 数据
     * @return key
     */
    String getKey(String binder);
}
