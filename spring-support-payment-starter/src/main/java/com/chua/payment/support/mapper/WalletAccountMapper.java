package com.chua.payment.support.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chua.payment.support.entity.WalletAccount;
import org.apache.ibatis.annotations.Mapper;

/**
   * 钱包账户 映射器
 * @author CH
 * @since 4.0.0
 */
@Mapper
public interface WalletAccountMapper extends BaseMapper<WalletAccount> {
}

