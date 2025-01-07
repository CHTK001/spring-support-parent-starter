package com.chua.starter.pay.support.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.mybatis.entity.Query;
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

    ReturnResult<PayMerchant> getOneByCode(@NotBlank(message = "商户编码不能为空") String merchantCode);

    /**
     * 保存支付商户信息
     *
     * @param payMerchant 待保存的支付商户对象
     * @return 返回保存后的支付商户对象
     */
    PayMerchant savePayMerchant(PayMerchant payMerchant);

    /**
     * 更新支付商户信息
     *
     * @param payMerchant 包含更新信息的支付商户对象
     * @return 返回更新结果，true表示成功，false表示失败
     */
    ReturnResult<Boolean> updatePayMerchant(PayMerchant payMerchant);

    /**
     * 删除支付商户信息
     *
     * @param payMerchantId 商户代码，用于标识要删除的商户
     * @return 返回删除结果，true表示成功，false表示失败
     */
    ReturnResult<Boolean> deletePayMerchant(Integer payMerchantId);

    /**
     * 分页查询支付商户信息
     *
     * @param query       分页查询参数
     * @param payMerchant
     * @return 返回分页查询结果
     */
    IPage<PayMerchant> pageForMerchant(Query<PayMerchant> query, PayMerchant payMerchant);
}
