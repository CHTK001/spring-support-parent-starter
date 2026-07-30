package com.chua.payment.support.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chua.payment.support.entity.WechatPayScoreOrder;
import org.apache.ibatis.annotations.Mapper;

/**
 * 微信支付分订单 Mapper
 */
@Mapper
public interface WechatPayScoreOrderMapper extends BaseMapper<WechatPayScoreOrder> {
}
