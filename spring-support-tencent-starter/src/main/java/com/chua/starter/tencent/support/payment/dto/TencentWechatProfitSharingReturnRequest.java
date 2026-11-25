package com.chua.starter.tencent.support.payment.dto;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 微信分账回退请求
 *
 * <p>对应微信支付 V3 接口 {@code POST /v3/profitsharing/return-orders}。</p>
 *
 * @param appId           应用 AppId
 * @param outOrderNo      原商户分账单号
 * @param outReturnNo     商户回退单号
 * @param returnMchid     回退接收方商户号
 * @param returnAmountFen 回退金额(分)
 * @param description     回退描述
 * @param extraParams     透传参数
 *
 * @author CH
 * @since 4.0.0
 */
public record TencentWechatProfitSharingReturnRequest(
        String appId,
        String outOrderNo,
        String outReturnNo,
        String returnMchid,
        Long returnAmountFen,
        String description,
        Map<String, Object> extraParams
) {

    /**
     * 规范构造器：透传参数做防御性拷贝。
     *
     * @param extraParams 透传参数
     */
    public TencentWechatProfitSharingReturnRequest {
        extraParams = extraParams == null ? null : Collections.unmodifiableMap(new LinkedHashMap<>(extraParams));
    }
}
