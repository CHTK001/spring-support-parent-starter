package com.chua.payment.support.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chua.payment.support.entity.OrderStateLog;
import org.apache.ibatis.annotations.Mapper;

/**
 * 订单状态流转日志 Mapper。
 *
 * <p>继承 {@link BaseMapper} 提供的单表通用 CRUD 能力，
 * 对应实体 {@link OrderStateLog} 与数据表 {@code pay_order_state_log}。
 * 本接口未声明额外方法，所有查询与写入均由 MyBatis-Plus 通用方法完成。</p>
 *
 * @since 2026-03-18
 * @author CH
 */
@Mapper
public interface OrderStateLogMapper extends BaseMapper<OrderStateLog> {
}
