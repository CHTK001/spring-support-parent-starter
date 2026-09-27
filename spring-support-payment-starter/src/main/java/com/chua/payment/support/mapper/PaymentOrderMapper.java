package com.chua.payment.support.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chua.payment.support.entity.PaymentOrder;
import org.apache.ibatis.annotations.Mapper;

/**
 * 支付订单 Mapper 接口。
 *
 * <p>继承 {@link BaseMapper} 提供的单表通用 CRUD 能力，
 * 对应实体 {@link PaymentOrder} 与数据表 {@code pay_order}。
 * 本接口未声明额外方法，所有查询与写入均由 MyBatis-Plus 通用方法完成。</p>
 *
 * @since 2026-03-18
 * @author CH
 */
@Mapper
public interface PaymentOrderMapper extends BaseMapper<PaymentOrder> {
}
