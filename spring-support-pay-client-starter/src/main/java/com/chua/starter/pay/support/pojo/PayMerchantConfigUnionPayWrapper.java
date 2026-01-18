package com.chua.starter.pay.support.pojo;

import com.chua.starter.pay.support.entity.PayMerchantConfigUnionPay;
import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 云闪付支付配置包装类
 *
 * @author CH
 * @since 2025/10/15 15:14
 */
@Data
@AllArgsConstructor
public class PayMerchantConfigUnionPayWrapper {

    /**
     * 云闪付支付配置
     */
    private PayMerchantConfigUnionPay payMerchantConfigUnionPay;

    /**
     * 是否有配置
     *
     * @return 是否有配置
     */
    public boolean hasConfig() {
        return null != payMerchantConfigUnionPay && payMerchantConfigUnionPay.getPayMerchantConfigStatus() == 1;
    }
}

