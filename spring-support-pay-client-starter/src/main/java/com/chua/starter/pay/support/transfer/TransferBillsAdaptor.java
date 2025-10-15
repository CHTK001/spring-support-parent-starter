package com.chua.starter.pay.support.transfer;

import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.pay.support.pojo.*;

/**
 * 转账适配器
 *
 * @author CH
 * @since 2025/10/15 10:12
 */
public interface TransferBillsAdaptor {

    /**
     * 创建转账订单
     *
     * @param request 转账请求参数 {@link CreateTransferV2Request}
     * @return 转账订单创建结果 {@link ReturnResult<CreateOrderV2Response>}
     */
    ReturnResult<CreateTransferV2Response> createOrder(CreateTransferV2Request request);
    /**
     * 查询转账状态
     * @param request 转账单号
     * @return 转账状态
     */
    ReturnResult<QueryTransferV2Response> status(QueryTransferV2Request request);
}
