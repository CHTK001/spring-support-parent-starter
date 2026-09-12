package com.chua.payment.support.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chua.payment.support.entity.PaymentRefundOrder;
import org.apache.ibatis.annotations.Mapper;

/**
* 退款单 映射器
* @author CH
* @since 4.0.0
 */
@Mapper
public interface PaymentRefundOrderMapper extends BaseMapper<PaymentRefundOrder> {
}

