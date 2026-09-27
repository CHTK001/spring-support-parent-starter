package com.chua.payment.support.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chua.payment.support.entity.PaymentSchedulerConfig;
import org.apache.ibatis.annotations.Mapper;

/**
 * 支付调度任务配置 Mapper。
 *
 * <p>继承 {@link BaseMapper} 提供的单表通用 CRUD 能力，
 * 对应实体 {@link PaymentSchedulerConfig} 与数据表 {@code pay_scheduler_config}。
 * 本接口未声明额外方法，所有查询与写入均由 MyBatis-Plus 通用方法完成。</p>
 *
 * @author CH
 * @since 4.0.0
 */
@Mapper
public interface PaymentSchedulerConfigMapper extends BaseMapper<PaymentSchedulerConfig> {
}
