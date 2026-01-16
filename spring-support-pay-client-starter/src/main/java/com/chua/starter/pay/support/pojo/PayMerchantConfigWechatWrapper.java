package com.chua.starter.pay.support.pojo;

import com.chua.starter.pay.support.entity.PayMerchantConfigWechat;
import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * @author CH
 * @since 2025/10/14 15:14
 */
@Data
@AllArgsConstructor
public class PayMerchantConfigWechatWrapper {

    /**
     * 微信支付配置
     */
    private PayMerchantConfigWechat payMerchantConfigWechat;

    /**
     * 是否有配置
     * @return 是否有配置
     */
    public boolean hasConfig() {
        return null != payMerchantConfigWechat && payMerchantConfigWechat.getPayMerchantConfigStatus() == 1;
    }
}
