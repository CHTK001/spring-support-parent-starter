package com.chua.payment.support.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chua.payment.support.entity.MerchantPaymentConfig;
import org.apache.ibatis.annotations.Mapper;

/**
 * 商户支付配置Mapper
 */
@Mapper
public interface MerchantPaymentConfigMapper extends BaseMapper<MerchantPaymentConfig> {
}
