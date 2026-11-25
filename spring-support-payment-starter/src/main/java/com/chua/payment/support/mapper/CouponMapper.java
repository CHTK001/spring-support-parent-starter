package com.chua.payment.support.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chua.payment.support.entity.Coupon;
import org.apache.ibatis.annotations.Mapper;

/**
 * 用户优惠券 Mapper
 *
 * @author CH
 * @since 4.0.0
 */
@Mapper
public interface CouponMapper extends BaseMapper<Coupon> {
}
