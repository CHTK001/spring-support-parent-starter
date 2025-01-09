package com.chua.starter.pay.support.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chua.starter.pay.support.entity.PayMerchant;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * @author CH
 * @since 2024/12/30
 */
@Mapper
public interface PayMerchantMapper extends BaseMapper<PayMerchant> {
    /**
     * 获取商户
     *
     * @param payMerchantCode 商户号
     * @param force           强制刷新
     * @return 商户
     */
    PayMerchant getMerchant(@Param("payMerchantCode") String payMerchantCode, @Param("force") boolean force);
}