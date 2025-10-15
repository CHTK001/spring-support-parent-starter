package com.chua.starter.pay.support.preprocess;

import com.chua.common.support.lang.code.ReturnResult;
import com.chua.common.support.spi.ServiceProvider;
import com.chua.starter.pay.support.entity.PayMerchantOrder;
import com.chua.starter.pay.support.pojo.RefundOrderV2Request;

/**
 * 订单退款预处理
 *
 * @author CH
 * @since 2025/10/14 14:14
 */
public interface PayRefundOrderPreprocess {

    /**
     * 创建处理器
     *
     * @return 创建处理器
     */
    static PayRefundOrderPreprocess createProcessor() {
        return ServiceProvider.of(PayRefundOrderPreprocess.class).getNewExtension("default");
    }

    /**
     * 预处理订单
     *
     * @param request          退款订单请求参数
     *                         示例: {
     *                         "refundOrderId": "20251014001",
     *                         "orderId": "20251014001",
     *                         "refundAmount": 100,
     *                         "refundReason": "用户取消订单"
     *                         }
     * @param payMerchantOrder 商户订单信息
     *                         示例: PayMerchantOrder对象
     * @return 预处理结果，包含预处理后的订单信息或错误信息
     *         成功示例: ReturnResult.success("预处理成功")
     *         失败示例: ReturnResult.failed("退款金额不能为负数")
     */
    ReturnResult<RefundOrderV2Request> preprocess(RefundOrderV2Request request, PayMerchantOrder payMerchantOrder);
}
