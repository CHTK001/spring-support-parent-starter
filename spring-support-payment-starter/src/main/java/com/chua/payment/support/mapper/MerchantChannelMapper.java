package com.chua.payment.support.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chua.payment.support.entity.MerchantChannel;
import org.apache.ibatis.annotations.Mapper;

/**
 * 商户渠道Mapper接口
 *
 * @author CH
 * @since 2026-03-18
 */
@Mapper
public interface MerchantChannelMapper extends BaseMapper<MerchantChannel> {
}
