package com.chua.starter.pay.support.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chua.starter.pay.support.entity.PayMerchantConfigWechat;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * @author CH
 * @since 2024/12/30
 */
@Mapper
public interface PayMerchantConfigWechatMapper extends BaseMapper<PayMerchantConfigWechat> {
    /**
     * 获取配置
     *
     * @param payMerchantCode 商户号
     * @param payMerchantOrderTradeType 交易方式；
     * @return 配置
     */
    PayMerchantConfigWechat getConfig(@Param("payMerchantCode") String payMerchantCode, @Param("payMerchantOrderTradeType") String payMerchantOrderTradeType);
}