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
    * 获取账户。
    * @param merchantId merchantid
    * @param userId 用户标识
    * @return 获取账户的结果
    */
    WalletAccountVO getAccount(Long merchantId, Long userId);

    /**
    * recharge。
    * @param dto dto
    * @return recharge的结果
    */
    WalletAccountVO recharge(WalletRechargeDTO dto);

    /**
    * 薪酬。
    * @param request 请求
    * @return 薪酬的结果
    */
    WalletAccountVO pay(WalletPayRequest request);

    /**
    * 退税。
    * @param request 请求
    * @return 退税的结果
    */
    WalletAccountVO refund(WalletRefundRequest request);

    /**
    * 调动。
    * @param dto dto
    */
    void transfer(WalletTransferOpDTO dto);

    /**
    * withdraw。
    * @param request 请求
    */
    void withdraw(WalletWithdrawRequest request);

    /**
    * 冻结余额（提现专用）
    * 可用_balance → frozen_balance，不立即扣减
    * @param dto dto
    * @return freezeForWithdraw的结果
    */
    WalletAccountVO freezeForWithdraw(WalletWithdrawDTO dto);

    /**
    * 确认提现到账（扣除冻结余额）
    * @param merchantId merchantid
    * @param userId 用户标识
    * @param bizNo bizno
    * @param amount amount
    */
    void confirmWithdraw(Long merchantId, Long userId, String bizNo, BigDecimal amount);

    /**
    * 取消提现（释放冻结余额回可用余额）
    * @param request 请求
    */
    void cancelWithdraw(WalletCancelWithdrawRequest request);

    /**
    * ensure账户。
    * @param merchantId merchantid
    * @param userId 用户标识
    */
    void ensureAccount(Long merchantId, Long userId);

    /**
    * 调动。
    * @param dto dto
    * @return 调动的结果
    */
    WalletAccountVO transfer(WalletTransferDTO dto);

    /**
    * withdraw。
    * @param dto dto
    * @return withdraw的结果
    */
    WalletAccountVO withdraw(WalletWithdrawDTO dto);

    /**
    * page账户。
    * @param pageNum pagenum
    * @param pageSize page大小
    * @param merchantId merchantid
    * @return page账户的结果
    */
    Page<WalletAccount> pageAccounts(int pageNum, int pageSize, Long merchantId);

    /**
    * freeze。
    * @param id 标识
    */
    void freeze(Long id);

    /**
    * unfreeze。
    * @param id 标识
    */
    void unfreeze(Long id);

    /**
    * 获取日志。
    * @param bizType biz类型
    * @param merchantId merchantid
    * @param userId 用户标识
    * @param bizNo bizno
    * @return 获取日志的结果
    */
    WalletAccountLog getLog(String bizType, Long merchantId, Long userId, String bizNo);

    /**
    * page日志。
    * @param request 请求
    * @return page日志的结果
    */
    Page<WalletAccountLog> pageLogs(WalletLogQueryRequest request);
}

