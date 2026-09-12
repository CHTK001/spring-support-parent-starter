package com.chua.payment.support.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chua.payment.support.entity.PaymentNotifyError;
import org.apache.ibatis.annotations.Mapper;

/**
   * 回调异常记录映射器
 * @author CH
 * @since 4.0.0
 */
@Mapper
public interface PaymentNotifyErrorMapper extends BaseMapper<PaymentNotifyError> {
}

