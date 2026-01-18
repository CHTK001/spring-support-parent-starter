package com.chua.starter.pay.support.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chua.starter.pay.support.entity.PayMerchantConfigAlipay;
import org.apache.ibatis.annotations.Mapper;

/**
 * 支付宝支付商户配置Mapper
 *
 * @author CH
 * @since 2025/10/15 11:23
 */
@Mapper
public interface PayMerchantConfigAlipayMapper extends BaseMapper<PayMerchantConfigAlipay> {
}

