package com.chua.payment.support.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.chua.payment.support.common.PageResult;
import com.chua.payment.support.common.Result;
import com.chua.payment.support.dto.WalletLogQueryRequest;
import com.chua.payment.support.dto.WalletRechargeDTO;
import com.chua.payment.support.entity.WalletAccountLog;
import com.chua.payment.support.service.WalletAccountService;
import com.chua.payment.support.vo.WalletAccountVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 钱包账户控制器
 * @author CH
 * @since 4.0.0
 */
@RestController
@RequestMapping("/wallet")
@RequiredArgsConstructor
public class WalletAccountController {

    /** Walletaccount服务 */
    private final WalletAccountService walletAccountService;

    @GetMapping("/account")
    /**
    * 获取账户
    * @param merchantId merchantid
    * @param userId 用户标识
    */
    public Result<WalletAccountVO> getAccount(@RequestParam Long merchantId,
                                              @RequestParam Long userId) {
        return Result.success(walletAccountService.getAccount(merchantId, userId));
    }

    @PostMapping("/account/recharge")
    /**
     * Recharge
     *
     * @param dto dto
     * @return recharge的结果
     */
    public Result<WalletAccountVO> recharge(@RequestBody WalletRechargeDTO dto) {
        return Result.success(walletAccountService.recharge(dto));
    }

    @GetMapping("/account/log/page")
    /**
     * page日志。
     * @param "1" "1"
     * @return page日志的结果
     */
    public Result<PageResult<WalletAccountLog>> pageLogs(@RequestParam(defaultValue = "1") int pageNum,
                                                          @RequestParam(defaultValue = "10") int pageSize,
                                                          @RequestParam(required = false) Long merchantId,
                                                          @RequestParam(required = false) Long userId,
                                                          @RequestParam(required = false) String bizType,
                                                          @RequestParam(required = false) String bizNo) {
        WalletLogQueryRequest request = new WalletLogQueryRequest(pageNum, pageSize, merchantId, userId, bizType, bizNo, null, null);
        Page<WalletAccountLog> page = walletAccountService.pageLogs(request);
        return Result.success(PageResult.of(page));
    }
}

