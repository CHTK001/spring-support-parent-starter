package com.chua.payment.support.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chua.payment.support.entity.WalletOrder;
import org.apache.ibatis.annotations.Mapper;

/**
 * 钱包订单Mapper
 */
@Mapper
public interface WalletOrderMapper extends BaseMapper<WalletOrder> {
}
