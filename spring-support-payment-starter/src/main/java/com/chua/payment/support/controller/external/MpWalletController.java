package com.chua.payment.support.controller.external;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.chua.payment.support.channel.WithdrawRequest;
import com.chua.payment.support.common.PageResult;
import com.chua.payment.support.common.Result;
import com.chua.payment.support.dto.WalletLogQueryRequest;
import com.chua.payment.support.dto.WalletOrderQueryRequest;
import com.chua.payment.support.dto.WalletTransferOpDTO;
import com.chua.payment.support.dto.WalletTransferDTO;
import com.chua.payment.support.dto.WalletWithdrawDTO;
import com.chua.payment.support.entity.WalletAccountLog;
import com.chua.payment.support.entity.WalletOrder;
import com.chua.payment.support.service.WalletAccountService;
import com.chua.payment.support.service.WalletOrderService;
import com.chua.payment.support.support.PaymentAccountPrincipalResolver;
import com.chua.payment.support.vo.WalletAccountVO;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
* 小程序钱包 API
* <p>小程序使用 OAuth2 登录流程获取 Bearer Token，认证机制与 Web 一致</p>
* @author CH
* @since 4.0.0
 */
@RestController
@RequestMapping("/mp/wallet")
@RequiredArgsConstructor
public class MpWalletController {

    /** Walletaccount服务 */
    private final WalletAccountService walletAccountService;
    /** Wallet排序服务 */
    private final WalletOrderService walletOrderService;
    /** Principal解析器 */
    private final PaymentAccountPrincipalResolver principalResolver;

    @GetMapping
    /**
    * 获取Wallet
    * @param request 请求
    * @param merchantId merchantid
    */
    public Result<WalletAccountVO> getWallet(HttpServletRequest request,
                                             @RequestParam Long merchantId) {
        Long userId = principalResolver.resolveUserId(request);
        if (userId == null) {
            return Result.error(401, "未获取到当前账号");
        }
        return Result.success(walletAccountService.getAccount(merchantId, userId));
    }

    @GetMapping("/log/page")
    /**
    * 列表日志
    * @param request 请求
    * @param pageNum pagenum
    * @param pageSize page大小
    * @param merchantId merchantid
    * @param bizType biz类型
    * @param bizNo bizno
    */
    public Result<PageResult<WalletAccountLog>> listLogs(HttpServletRequest request,
                                                         @RequestParam(defaultValue = "1") int pageNum,
                                                         @RequestParam(defaultValue = "10") int pageSize,
                                                         @RequestParam Long merchantId,
                                                         @RequestParam(required = false) String bizType,
                                                         @RequestParam(required = false) String bizNo) {
        Long userId = principalResolver.resolveUserId(request);
        if (userId == null) {
            return Result.error(401, "未获取到当前账号");
        }
        Page<WalletAccountLog> page = walletAccountService.pageLogs(new WalletLogQueryRequest(pageNum, pageSize, merchantId, userId, bizType, bizNo, null, null));
        return Result.success(PageResult.of(page));
    }

    @GetMapping("/order/page")
    /**
    * 列表订单
    * @param request 请求
    * @param pageNum pagenum
    * @param pageSize page大小
    * @param merchantId merchantid
    * @param orderType 订单类型
    * @param status 状态
    */
    public Result<PageResult<WalletOrder>> listOrders(HttpServletRequest request,
                                                      @RequestParam(defaultValue = "1") int pageNum,
                                                      @RequestParam(defaultValue = "10") int pageSize,
                                                      @RequestParam Long merchantId,
                                                      @RequestParam(required = false) String orderType,
                                                      @RequestParam(required = false) String status) {
        Long userId = principalResolver.resolveUserId(request);
        if (userId == null) {
            return Result.error(401, "未获取到当前账号");
        }
        Page<WalletOrder> page = walletOrderService.page(new WalletOrderQueryRequest(pageNum, pageSize, merchantId, userId, orderType, status));
        return Result.success(PageResult.of(page));
    }

    @PostMapping("/withdraw")
    /**
    * Withdraw
    * @param request 请求
    * @param dto dto
    */
    public Result<WalletOrder> withdraw(HttpServletRequest request,
                                        @RequestBody WalletWithdrawDTO dto) {
        Long userId = principalResolver.resolveUserId(request);
        if (userId == null) {
            return Result.error(401, "未获取到当前账号");
        }
        if (dto.getMerchantId() == null) {
            return Result.error("商户ID不能为空");
        }
        if (dto.getAmount() == null || dto.getAmount().compareTo(java.math.BigDecimal.ZERO) <= 0) {
            return Result.error("提现金额必须大于0");
        }
        if (!StringUtils.hasText(dto.getBankAccount())) {
            return Result.error("银行账号不能为空");
        }

        // 强检验
        dto.setUserId(userId);
        dto.setOperator(String.valueOf(userId));

        walletAccountService.freezeForWithdraw(dto);

        WithdrawRequest withdrawRequest = new WithdrawRequest();
        withdrawRequest.setWithdrawNo(dto.getWithdrawNo());
        withdrawRequest.setMerchantId(dto.getMerchantId());
        withdrawRequest.setUserId(userId);
        withdrawRequest.setAmount(dto.getAmount());
        withdrawRequest.setBankAccount(dto.getBankAccount());
        withdrawRequest.setBankName(dto.getBankName());
        withdrawRequest.setAccountName(dto.getAccountName());
        withdrawRequest.setNotifyUrl(dto.getNotifyUrl());
        withdrawRequest.setOperator(String.valueOf(userId));
        withdrawRequest.setRemark(dto.getRemark());

        WalletOrder order = walletOrderService.createWithdrawOrder(withdrawRequest);
        walletOrderService.markProcessing(order.getOrderNo(), null, null);
        return Result.success(order);
    }

    @PostMapping("/transfer")
    /**
    * 调动
    * @param request 请求
    * @param dto dto
    */
    public Result<Void> transfer(HttpServletRequest request,
                                 @RequestBody WalletTransferDTO dto) {
        Long userId = principalResolver.resolveUserId(request);
        if (userId == null) {
            return Result.error(401, "未获取到当前账号");
        }
        if (dto.getMerchantId() == null) {
            return Result.error("商户ID不能为空");
        }
        if (dto.getToUserId() == null) {
            return Result.error("转入用户不能为空");
        }
        if (dto.getAmount() == null || dto.getAmount().compareTo(java.math.BigDecimal.ZERO) <= 0) {
            return Result.error("转账金额必须大于0");
        }
        if (userId.equals(dto.getToUserId())) {
            return Result.error("不能转账给自己");
        }

        String transferNo = "TRF" + System.currentTimeMillis() + UUID.randomUUID().toString().substring(0, 6).toUpperCase();

        walletAccountService.transfer(new WalletTransferOpDTO(
                dto.getMerchantId(), userId, dto.getToUserId(),
                dto.getAmount(), transferNo, String.valueOf(userId), dto.getRemark()));
        return Result.success();
    }
}

