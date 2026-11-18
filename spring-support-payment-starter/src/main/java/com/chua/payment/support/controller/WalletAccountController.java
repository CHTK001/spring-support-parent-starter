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

    /**
     * 钱包账户服务
     */
    private final WalletAccountService walletAccountService;

    @GetMapping("/account")
    /**
     * 获取账户
     * @param merchantId 商户标识
     * @param userId 用户标识
     * @return 钱包账户信息
     */
    public Result<WalletAccountVO> getAccount(@RequestParam Long merchantId,
                                              @RequestParam Long userId) {
        return Result.success(walletAccountService.getAccount(merchantId, userId));
    }

    @PostMapping("/account/recharge")
    /**
     * 充值
     * @param dto dto
     * @return 返回值
    */
    public Result<WalletAccountVO> recharge(@RequestBody WalletRechargeDTO dto) {
        return Result.success(walletAccountService.recharge(dto));
    }

    @GetMapping("/account/log/page")
    /**
     * 分页查询账户流水
     * @param pageNum    页码
     * @param pageSize   每页条数
     * @param merchantId 商户标识（可选）
     * @param userId     用户标识（可选）
     * @param bizType    业务类型（可选）
     * @param bizNo      业务单号（可选）
     * @return 分页查询账户流水的结果
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
