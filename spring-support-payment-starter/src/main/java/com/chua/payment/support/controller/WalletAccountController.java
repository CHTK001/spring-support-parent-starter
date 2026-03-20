package com.chua.payment.support.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.chua.payment.support.common.PageResult;
import com.chua.payment.support.common.Result;
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
 */
@RestController
@RequestMapping("/api/wallet")
@RequiredArgsConstructor
public class WalletAccountController {

    private final WalletAccountService walletAccountService;

    @GetMapping("/account")
    public Result<WalletAccountVO> getAccount(@RequestParam Long merchantId,
                                              @RequestParam Long userId) {
        return Result.success(walletAccountService.getAccount(merchantId, userId));
    }

    @PostMapping("/account/recharge")
    public Result<WalletAccountVO> recharge(@RequestBody WalletRechargeDTO dto) {
        return Result.success(walletAccountService.recharge(dto));
    }

    @GetMapping("/account/log/page")
    public Result<PageResult<WalletAccountLog>> pageLogs(@RequestParam(defaultValue = "1") int pageNum,
                                                         @RequestParam(defaultValue = "10") int pageSize,
                                                         @RequestParam(required = false) Long merchantId,
                                                         @RequestParam(required = false) Long userId,
                                                         @RequestParam(required = false) String bizType,
                                                         @RequestParam(required = false) String bizNo) {
        Page<WalletAccountLog> page = walletAccountService.pageLogs(pageNum, pageSize, merchantId, userId, bizType, bizNo);
        return Result.success(PageResult.of(page));
    }
}
