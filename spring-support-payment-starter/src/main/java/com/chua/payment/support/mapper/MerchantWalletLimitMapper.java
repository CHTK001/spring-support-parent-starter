package com.chua.payment.support.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chua.payment.support.entity.MerchantWalletLimit;
import org.apache.ibatis.annotations.Mapper;

/**
 * 商户钱包限额Mapper
 */
@Mapper
public interface MerchantWalletLimitMapper extends BaseMapper<MerchantWalletLimit> {
}
