package com.chua.payment.support.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chua.payment.support.entity.PaymentNotifyError;
import org.apache.ibatis.annotations.Mapper;

/**
 * 回调异常记录Mapper
 */
@Mapper
public interface PaymentNotifyErrorMapper extends BaseMapper<PaymentNotifyError> {
}
