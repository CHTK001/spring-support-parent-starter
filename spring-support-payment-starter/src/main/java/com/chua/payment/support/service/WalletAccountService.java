package com.chua.payment.support.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.chua.payment.support.dto.WalletRechargeDTO;
import com.chua.payment.support.entity.WalletAccountLog;
import com.chua.payment.support.vo.WalletAccountVO;

import java.math.BigDecimal;

/**
 * 钱包账户服务
 */
public interface WalletAccountService {

    WalletAccountVO getAccount(Long merchantId, Long userId);

    WalletAccountVO recharge(WalletRechargeDTO dto);

    WalletAccountVO pay(Long merchantId, Long userId, BigDecimal amount, String orderNo, String operator, String remark);

    WalletAccountVO refund(Long merchantId, Long userId, BigDecimal amount, String refundNo, String operator, String remark);

    void transfer(Long merchantId,
                  Long fromUserId,
                  Long toUserId,
                  BigDecimal amount,
                  String transferNo,
                  String operator,
                  String remark);

    void withdraw(Long merchantId, Long userId, BigDecimal amount, String withdrawNo, String operator, String remark);

    WalletAccountLog getLog(String bizType, Long merchantId, Long userId, String bizNo);

    Page<WalletAccountLog> pageLogs(int pageNum, int pageSize, Long merchantId, Long userId, String bizType, String bizNo);
}
