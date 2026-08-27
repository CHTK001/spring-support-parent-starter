package com.chua.payment.support.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.chua.payment.support.dto.WalletCancelWithdrawRequest;
import com.chua.payment.support.dto.WalletLogQueryRequest;
import com.chua.payment.support.dto.WalletNotifyDTO;
import com.chua.payment.support.dto.WalletPayRequest;
import com.chua.payment.support.dto.WalletRefundRequest;
import com.chua.payment.support.dto.WalletRechargeDTO;
import com.chua.payment.support.dto.WalletTransferOpDTO;
import com.chua.payment.support.dto.WalletTransferDTO;
import com.chua.payment.support.dto.WalletWithdrawDTO;
import com.chua.payment.support.dto.WalletWithdrawRequest;
import com.chua.payment.support.entity.WalletAccountLog;
import com.chua.payment.support.entity.WalletAccount;
import com.chua.payment.support.vo.WalletAccountVO;

import java.math.BigDecimal;
import java.util.List;

/**
 * 钱包账户服务接口，定义充值、支付、退款、转账、提现等操作契约。
 *
 * @author CH
 * @since 4.0.0.50
 */
public interface WalletAccountService {

    WalletAccountVO getAccount(Long merchantId, Long userId);

    WalletAccountVO recharge(WalletRechargeDTO dto);

    WalletAccountVO pay(WalletPayRequest request);

    WalletAccountVO refund(WalletRefundRequest request);

    void transfer(WalletTransferOpDTO dto);

    void withdraw(WalletWithdrawRequest request);

    /**
     * 冻结余额（提现专用）
     * available_balance → frozen_balance，不立即扣减
     */
    WalletAccountVO freezeForWithdraw(WalletWithdrawDTO dto);

    /**
     * 确认提现到账（扣除冻结余额）
     */
    void confirmWithdraw(Long merchantId, Long userId, String bizNo, BigDecimal amount);

    /**
     * 取消提现（释放冻结余额回可用余额）
     */
    void cancelWithdraw(WalletCancelWithdrawRequest request);

    void ensureAccount(Long merchantId, Long userId);

    WalletAccountVO transfer(WalletTransferDTO dto);

    WalletAccountVO withdraw(WalletWithdrawDTO dto);

    Page<WalletAccount> pageAccounts(int pageNum, int pageSize, Long merchantId);

    void freeze(Long id);

    void unfreeze(Long id);

    WalletAccountLog getLog(String bizType, Long merchantId, Long userId, String bizNo);

    Page<WalletAccountLog> pageLogs(WalletLogQueryRequest request);
}

