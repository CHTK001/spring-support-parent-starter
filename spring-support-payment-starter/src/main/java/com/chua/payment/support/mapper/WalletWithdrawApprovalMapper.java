package com.chua.payment.support.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chua.payment.support.entity.WalletWithdrawApproval;
import org.apache.ibatis.annotations.Mapper;

/**
 * 钱包提现审批 Mapper
 * @author CH
 */
@Mapper
public interface WalletWithdrawApprovalMapper extends BaseMapper<WalletWithdrawApproval> {
}

