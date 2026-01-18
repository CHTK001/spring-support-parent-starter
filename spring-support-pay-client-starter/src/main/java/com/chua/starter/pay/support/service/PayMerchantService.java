package com.chua.starter.pay.support.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.chua.starter.mybatis.entity.Query;
import com.chua.starter.pay.support.entity.PayMerchant;
import com.baomidou.mybatisplus.extension.service.IService;
import com.chua.starter.pay.support.pojo.PayMerchantWrapper;

import java.util.List;

/**
 * 商户服务接口
 *
 * @author CH
 * @since 2025-10-14
 */
public interface PayMerchantService extends IService<PayMerchant> {

    /**
     * 根据商户ID获取商户信息
     *
     * @param payMerchantId 商户ID
     * @return 商户信息
     */
    PayMerchantWrapper getByCodeForPayMerchant(Integer payMerchantId);

    /**
     * 更新商户信息
     *
     * @param payMerchant 商户信息
     * @return 更新后的商户信息
     */
    Boolean updateForPayMerchant(PayMerchant payMerchant);

    /**
     * 保存商户信息
     *
     * @param payMerchant 商户信息
     * @return 是否保存成功
     */
    boolean saveForPayMerchant(PayMerchant payMerchant);

    /**
     * 根据商户编码获取商户信息
     *
     * @param payMerchantCode 商户编码
     * @return 商户信息
     */
    PayMerchant getByCodeForPayMerchantCode(Integer payMerchantCode);

    /**
     * 分页查询商户信息
     *
     * @param page          分页参数
     * @param payMerchant 查询参数
     * @return 商户信息
     */
    IPage<PayMerchant> pageForMerchant(Query<PayMerchant> page, PayMerchant payMerchant);

    /**
     * 获取所有有效的商户信息
     *
     * @return 商户信息
     */
    List<PayMerchant> allEffective();
}
