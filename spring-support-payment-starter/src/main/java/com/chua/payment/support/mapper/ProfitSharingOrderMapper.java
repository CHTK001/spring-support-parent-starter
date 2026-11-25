package com.chua.payment.support.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chua.payment.support.entity.ProfitSharingOrder;
import org.apache.ibatis.annotations.Mapper;

/**
 * 分账订单 Mapper
 *
 * @author CH
 * @since 4.0.0
 */
@Mapper
public interface ProfitSharingOrderMapper extends BaseMapper<ProfitSharingOrder> {
}
