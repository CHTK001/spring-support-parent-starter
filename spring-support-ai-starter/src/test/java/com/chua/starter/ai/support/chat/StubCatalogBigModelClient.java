package com.chua.starter.ai.support.chat;

import com.chua.common.support.ai.bigmodel.BigModelCallback;
import com.chua.common.support.ai.bigmodel.BigModelClient;
import com.chua.common.support.ai.bigmodel.BigModelMetadataView;
import com.chua.common.support.ai.bigmodel.BigModelPricing;
import com.chua.common.support.ai.bigmodel.BigModelRequest;
import com.chua.common.support.ai.bigmodel.BigModelResponse;

import java.math.BigDecimal;
import java.util.List;

/**
 * 模型目录测试桩。
 */
final class StubCatalogBigModelClient implements BigModelClient {

    @Override
    public List<BigModelMetadataView> listModelViews() {
        return List.of(
                new BigModelMetadataView("model-a", "1", true, "mock", "a", false, false, freePricing(), freePricing()),
                new BigModelMetadataView("model-b", "1", true, "mock", "b", false, false, freePricing(), freePricing())
        );
    }

    @Override
    public void callStream(BigModelRequest request, BigModelCallback callback) {
        callback.accept(BigModelResponse.builder().output("").done(true).build());
        callback.onComplete();
    }

    private BigModelPricing freePricing() {
        return new BigModelPricing("free", "USD", BigDecimal.ZERO, BigDecimal.ONE, BigDecimal.ZERO, true);
    }
}
