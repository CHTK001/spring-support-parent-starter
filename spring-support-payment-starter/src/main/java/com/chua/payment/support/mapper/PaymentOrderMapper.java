package com.chua.payment.support.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chua.payment.support.entity.PaymentOrder;
import org.apache.ibatis.annotations.Mapper;

/**
   * 支付订单映射器接口
 *
 * @since 2026-03-18
 * @author CH
 */
@Mapper
public interface PaymentOrderMapper extends BaseMapper<PaymentOrder> {
}
