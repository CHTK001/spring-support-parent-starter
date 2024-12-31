package com.chua.starter.pay.support.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.chua.starter.pay.support.entity.PayMerchant;
import jakarta.validation.constraints.NotBlank;

/**
 * @author CH
 * @since 2024/12/30
 */
public interface PayMerchantService extends IService<PayMerchant> {

    /**
     * 根据商户编码获取商户信息
     *
     * @param merchantCode 商户编码
     * @return 商户信息
     */

    PayMerchant getOneByCode(@NotBlank(message = "商户编码不能为空") String merchantCode);
    /**
     * 根据商户编码获取商户信息
     *
     * @param merchantCode 商户编码
     * @param force        是否强制查询
     * @return 商户信息
     */
    PayMerchant getOneByCode(@NotBlank(message = "商户编码不能为空") String merchantCode, boolean force);
}
