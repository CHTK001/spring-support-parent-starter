package com.chua.starter.ai.support.service;

/**
 * AI 模型价格同步服务接口。
 *
 * @since 4.0.0.42
 * @author CH
 */
public interface AiModelPriceSyncService {

    /**
     * 同步模型价格。
     */
    void syncPrices();

    /**
     * 同步模型列表。
     */
    void syncModelList();

    /**
     * 按厂商编码同步模型价格。
     *
     * @param code 厂商编码
     */
    default void syncPrices(String code) {
        /**
         * 同步prices。
         */
        syncPrices();
    }

    /**
     * 按厂商编码替换模型列表。
     *
     * @param code 厂商编码
     */
    default void replaceModels(String code) {
        /**
         * 同步模型列表。
         */
        syncModelList();
    }
}
