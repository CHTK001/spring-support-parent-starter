package com.chua.starter.pay.support.preprocess;

import com.chua.common.support.lang.code.ReturnResult;
import com.chua.common.support.spi.ServiceProvider;
import com.chua.starter.pay.support.pojo.CreateOrderV2Request;

/**
 * 订单预处理
 *
 * @author CH
 * @since 2025/10/14 14:14
 */
public interface PayCreateOrderPreprocess {

    /**
     * 创建处理器
     *
     * @return 创建处理器
     */
    static PayCreateOrderPreprocess createProcessor() {
        return ServiceProvider.of(PayCreateOrderPreprocess.class).getNewExtension("default");
    }

    /**
     * 预处理订单
     *
     * @param request 创建订单请求参数
     *                示例: {
     *                "orderId": "20251014001",
     *                "amount": 100,
     *                "productId": "P10001",
     *                "productName": "商品名称",
     *                "productDesc": "商品描述",
     *                "notifyUrl": "https://example.com/notify",
     *                "returnUrl": "https://example.com/return"
     *                }
     * @param userId  用户ID
     *                示例: "USER_001"
     * @param openId  微信开放ID（可为空）
     *                示例: "oHxwG5VH1H5VH1H5VH1H5VH1H5V"
     * @return 预处理结果，包含预处理后的订单信息或错误信息
     *         成功示例: ReturnResult.success("预处理成功")
     *         失败示例: ReturnResult.failed("订单金额不能为负数")
     */
    ReturnResult<CreateOrderV2Request> preprocess(CreateOrderV2Request request, String userId, String openId);
}
