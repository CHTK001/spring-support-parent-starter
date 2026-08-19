package com.chua.payment.support.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chua.payment.support.entity.OrderStateLog;
import org.apache.ibatis.annotations.Mapper;

/**
 * 订单状态流转日志 Mapper
 *
 * @since 2026-03-18
 * @author CH
 */
@Mapper
public interface OrderStateLogMapper extends BaseMapper<OrderStateLog> {
}
