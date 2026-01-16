package com.chua.starter.pay.support.preprocess;

import com.chua.common.support.lang.code.ReturnResult;
import com.chua.common.support.spi.ServiceProvider;
import com.chua.starter.pay.support.pojo.CreateTransferV2Request;

/**
 * 转账预处理
 *
 * @author CH
 * @since 2025/10/15 10:21
 */
public interface PayTransferPreprocess {

    /**
     * 创建转账预处理器实例
     *
     * @return 转账预处理器实例
     */
    static PayTransferPreprocess createProcessor() {
        return ServiceProvider.of(PayTransferPreprocess.class).getExtension("default");
    }

    /**
     * 预处理转账请求
     *
     * @param request 转账请求参数 {@link CreateTransferV2Request}
     * @return 预处理结果，包含处理后的转账请求数据
     */
    ReturnResult<CreateTransferV2Request> preprocess(CreateTransferV2Request request);
}
