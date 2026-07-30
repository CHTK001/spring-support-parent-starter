package com.chua.payment.support.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chua.payment.support.entity.WalletAccountLog;
import org.apache.ibatis.annotations.Mapper;

/**
 * 钱包账户流水 Mapper
 */
@Mapper
public interface WalletAccountLogMapper extends BaseMapper<WalletAccountLog> {
}
