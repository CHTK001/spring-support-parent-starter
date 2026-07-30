package com.chua.payment.support.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chua.payment.support.entity.PaymentSchedulerConfig;
import org.apache.ibatis.annotations.Mapper;

/**
 * 支付调度任务配置 Mapper
 */
@Mapper
public interface PaymentSchedulerConfigMapper extends BaseMapper<PaymentSchedulerConfig> {
}
