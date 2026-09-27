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

    /**
     * 获取钱包账户。
     * @param merchantId 商户标识
     * @param userId 用户标识
     * @return 获取到的钱包账户
     */
    WalletAccountVO getAccount(Long merchantId, Long userId);

    /**
     * 充值。
     * @param dto dto
     */
    WalletAccountVO recharge(WalletRechargeDTO dto);

    /**
     * 支付。
     * @param request 请求参数
     * @return 支付结果
     */
    WalletAccountVO pay(WalletPayRequest request);

    /**
     * 退款。
     * @param request 请求参数
     * @return 退款结果
     */
    WalletAccountVO refund(WalletRefundRequest request);

    /**
     * 转账。
     * @param dto dto
     */
    void transfer(WalletTransferOpDTO dto);

    /**
     * 提现。
     * @param request 请求参数
     */
    void withdraw(WalletWithdrawRequest request);

    /**
     * 冻结提现余额（提现专用），从可用余额划转到冻结余额，不立即扣减。
     * @param dto dto
     */
    WalletAccountVO freezeForWithdraw(WalletWithdrawDTO dto);

    /**
     * 确认提现到账（扣减冻结余额）。
     * @param merchantId 商户标识
     * @param userId 用户标识
     * @param bizNo 业务单号
     * @param amount 金额
     */
    void confirmWithdraw(Long merchantId, Long userId, String bizNo, BigDecimal amount);

    /**
     * 取消提现（释放冻结余额回可用余额）。
     * @param request 请求参数
     */
    void cancelWithdraw(WalletCancelWithdrawRequest request);

    /**
     * 确保账户存在，不存在则初始化。
     * @param merchantId 商户标识
     * @param userId 用户标识
     */
    void ensureAccount(Long merchantId, Long userId);

    /**
     * 转账。
     * @param dto dto
     * @return 转账结果
     */
    WalletAccountVO transfer(WalletTransferDTO dto);

    /**
     * 提现。
     * @param dto dto
     */
    WalletAccountVO withdraw(WalletWithdrawDTO dto);

    /**
     * 分页查询钱包账户。
     * @param pageNum 页码
     * @param pageSize 每页大小
     * @param merchantId 商户标识
     * @return 钱包账户分页结果
     */
    Page<WalletAccount> pageAccounts(int pageNum, int pageSize, Long merchantId);

    /**
     * 冻结账户。
     * @param id 标识
     */
    void freeze(Long id);

    /**
     * 解冻账户。
     * @param id 标识
     */
    void unfreeze(Long id);

    /**
     * 获取账户流水日志。
     * @param bizType 业务类型
     * @param merchantId 商户标识
     * @param userId 用户标识
     * @param bizNo 业务单号
     * @return 获取到的账户流水日志
     */
    WalletAccountLog getLog(String bizType, Long merchantId, Long userId, String bizNo);

    /**
     * 分页查询账户流水日志。
     * @param request 请求参数
     * @return 账户流水日志分页结果
     */
    Page<WalletAccountLog> pageLogs(WalletLogQueryRequest request);
}
