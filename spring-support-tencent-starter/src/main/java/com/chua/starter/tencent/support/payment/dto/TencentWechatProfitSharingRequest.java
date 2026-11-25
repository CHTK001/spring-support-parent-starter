package com.chua.starter.tencent.support.payment.dto;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 微信分账创建请求
 *
 * <p>对应微信支付 V3 接口 {@code POST /v3/profitsharing/orders}。</p>
 *
 * @param appId       应用 AppId
 * @param transactionId 微信支付订单号
 * @param outOrderNo  商户分账单号
 * @param receivers   分账接收方列表，元素形如 {type, account, amount, description}
 * @param finishType  分账完成类型：FORCE_FINISH-强制完成(金额为0部分会解冻)，缺省由微信侧判定
 * @param extraParams 透传参数
 *
 * @author CH
 * @since 4.0.0
 */
public record TencentWechatProfitSharingRequest(
        String appId,
        String transactionId,
        String outOrderNo,
        List<Map<String, Object>> receivers,
        String finishType,
        Map<String, Object> extraParams
) {

    /**
     * 规范构造器：接收方列表与透传参数做防御性拷贝。
     *
     * @param receivers   分账接收方列表
     * @param extraParams 透传参数
     */
    public TencentWechatProfitSharingRequest {
        receivers = receivers == null ? null : Collections.unmodifiableList(new ArrayList<>(receivers));
        extraParams = extraParams == null ? null : Collections.unmodifiableMap(new LinkedHashMap<>(extraParams));
    }
}
