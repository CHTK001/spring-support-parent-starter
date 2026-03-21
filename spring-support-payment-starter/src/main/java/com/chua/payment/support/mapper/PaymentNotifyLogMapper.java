package com.chua.payment.support.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chua.payment.support.entity.PaymentNotifyLog;
import org.apache.ibatis.annotations.Mapper;

/**
 * 支付回调日志 Mapper
 */
@Mapper
public interface PaymentNotifyLogMapper extends BaseMapper<PaymentNotifyLog> {
}
