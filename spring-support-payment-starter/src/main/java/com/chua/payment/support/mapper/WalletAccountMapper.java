package com.chua.payment.support.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chua.payment.support.entity.WalletAccount;
import org.apache.ibatis.annotations.Mapper;

/**
 * 钱包账户 Mapper
 */
@Mapper
public interface WalletAccountMapper extends BaseMapper<WalletAccount> {
}
