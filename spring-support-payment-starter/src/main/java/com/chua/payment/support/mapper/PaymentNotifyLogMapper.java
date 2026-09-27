package com.chua.payment.support.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chua.payment.support.entity.PaymentNotifyLog;
import org.apache.ibatis.annotations.Mapper;

/**
 * 支付回调日志 Mapper。
 *
 * <p>继承 {@link BaseMapper} 提供的单表通用 CRUD 能力，
 * 对应实体 {@link PaymentNotifyLog} 与数据表 {@code pay_notify_log}。
 * 本接口未声明额外方法，所有查询与写入均由 MyBatis-Plus 通用方法完成。</p>
 *
 * @author CH
 * @since 4.0.0
 */
@Mapper
public interface PaymentNotifyLogMapper extends BaseMapper<PaymentNotifyLog> {
}
