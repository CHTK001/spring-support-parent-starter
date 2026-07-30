package com.chua.payment.support.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chua.payment.support.entity.PaymentOrder;
import org.apache.ibatis.annotations.Mapper;

/**
 * 支付订单Mapper接口
 *
 * @author CH
 * @since 2026-03-18
 */
@Mapper
public interface PaymentOrderMapper extends BaseMapper<PaymentOrder> {
}
