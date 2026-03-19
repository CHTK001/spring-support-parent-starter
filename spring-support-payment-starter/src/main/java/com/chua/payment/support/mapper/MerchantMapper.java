package com.chua.payment.support.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chua.payment.support.entity.Merchant;
import org.apache.ibatis.annotations.Mapper;

/**
 * 商户Mapper接口
 *
 * @author CH
 * @since 2026-03-18
 */
@Mapper
public interface MerchantMapper extends BaseMapper<Merchant> {
}
