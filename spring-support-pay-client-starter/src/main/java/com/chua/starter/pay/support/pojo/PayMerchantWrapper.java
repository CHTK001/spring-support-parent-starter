package com.chua.starter.pay.support.pojo;

import com.chua.starter.pay.support.entity.PayMerchant;
import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 商户信息
 * @author CH
 * @since 2025/10/14 15:10
 */
@Data
@AllArgsConstructor
public class PayMerchantWrapper {


    /**
     * 商户信息
     */
    private PayMerchant payMerchant;

    /**
     * 是否有商户信息
     * @return 是否有商户信息
     */
    public boolean hasMerchant() {
        return null != payMerchant;
    }
}
