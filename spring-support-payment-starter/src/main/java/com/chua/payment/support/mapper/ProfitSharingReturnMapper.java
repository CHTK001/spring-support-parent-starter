package com.chua.payment.support.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chua.payment.support.entity.ProfitSharingReturn;
import org.apache.ibatis.annotations.Mapper;

/**
 * 分账回退 Mapper
 *
 * @author CH
 * @since 4.0.0
 */
@Mapper
public interface ProfitSharingReturnMapper extends BaseMapper<ProfitSharingReturn> {
}
