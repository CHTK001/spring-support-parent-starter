package com.chua.starter.pay.support.pojo;

import com.chua.starter.pay.support.entity.PayMerchantConfigAlipay;
import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 支付宝支付配置包装类
 *
 * @author CH
 * @since 2025/10/15 15:14
 */
@Data
@AllArgsConstructor
public class PayMerchantConfigAlipayWrapper {

    /**
     * 支付宝支付配置
     */
    private PayMerchantConfigAlipay payMerchantConfigAlipay;

    /**
     * 是否有配置
     *
     * @return 是否有配置
     */
    public boolean hasConfig() {
        return null != payMerchantConfigAlipay && payMerchantConfigAlipay.getPayMerchantConfigStatus() == 1;
    }
}

