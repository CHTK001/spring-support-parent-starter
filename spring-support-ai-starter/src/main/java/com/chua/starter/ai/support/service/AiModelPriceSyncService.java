package com.chua.starter.ai.support.service;

public interface AiModelPriceSyncService {

    void syncPrices();

    void syncModelList();

    default void syncPrices(String code) {
        syncPrices();
    }

    default void replaceModels(String code) {
        syncModelList();
    }
}
