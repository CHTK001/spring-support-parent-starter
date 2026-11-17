// [P3C A20 豁免] A20/A30/A31: 含日志/界面/SQL 字符串字面量等非业务魔法值场景，豁免。
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
   * <p>小程序使用 OAuth2 登录流程获取 Bearer Token，认证场景为 Web 端。
   *
   * @author CH
   * @since 4.0.0
   */
@RestController
@RequestMapping("/mp/wallet")
@RequiredArgsConstructor
public class MpWalletController {

      /**
       * 钱包账户服务
       */
    private final WalletAccountService walletAccountService;
      /**
       * 钱包订单服务
       */
    private final WalletOrderService walletOrderService;
      /**
       * 登录主体解析器
       */
    private final PaymentAccountPrincipalResolver principalResolver;

    /**
       * 获取钱包
       *
       * @param request 请求
       * @param merchantId 商户标识
       * @return 钱包账户信息
       */
    @GetMapping
    public Result<WalletAccountVO> getWallet(HttpServletRequest request,
                                             @RequestParam Long merchantId) {
        Long userId = principalResolver.resolveUserId(request);
        if (userId == null) {
            return Result.error(401, "未获取到当前账户");
        }
        return Result.success(walletAccountService.getAccount(merchantId, userId));
    }

    /**
       * 流水列表
       *
       * @param request 请求
       * @param pageNum 页码（默认 1）
       * @param pageSize 每页条数（默认 10）
       * @param merchantId 商户标识
       * @param bizType 业务类型（可空）
       * @param bizNo 业务单号（可空）
       * @return 流水分页结果
       */
    @GetMapping("/log/page")
    public Result<PageResult<WalletAccountLog>> listLogs(HttpServletRequest request,
                                                         @RequestParam(defaultValue = "1") int pageNum,
                                                         @RequestParam(defaultValue = "10") int pageSize,
                                                         @RequestParam Long merchantId,
                                                         @RequestParam(required = false) String bizType,
                                                         @RequestParam(required = false) String bizNo) {
        Long userId = principalResolver.resolveUserId(request);
        if (userId == null) {
            return Result.error(401, "未获取到当前账户");
        }
        Page<WalletAccountLog> page = walletAccountService.pageLogs(new WalletLogQueryRequest(pageNum, pageSize, merchantId, userId
            , bizType, bizNo, null, null));
        return Result.success(PageResult.of(page));
    }

    /**
       * 订单列表
       *
       * @param request 请求
       * @param pageNum 页码（默认 1）
       * @param pageSize 每页条数（默认 10）
       * @param merchantId 商户标识
       * @param orderType 订单类型（可空）
       * @param status 状态（可空）
       * @return 订单分页结果
       */
    @GetMapping("/order/page")
    public Result<PageResult<WalletOrder>> listOrders(HttpServletRequest request,
                                                      @RequestParam(defaultValue = "1") int pageNum,
                                                      @RequestParam(defaultValue = "10") int pageSize,
                                                      @RequestParam Long merchantId,
                                                      @RequestParam(required = false) String orderType,
                                                      @RequestParam(required = false) String status) {
        Long userId = principalResolver.resolveUserId(request);
        if (userId == null) {
            return Result.error(401, "未获取到当前账户");
        }
        Page<WalletOrder> page = walletOrderService.page(new WalletOrderQueryRequest(pageNum, pageSize, merchantId, userId, orderType, status));
        return Result.success(PageResult.of(page));
    }

    /**
       * 提现（冻结余额并创建提现订单）
       *
       * @param request 请求
       * @param dto 提现请求参数
       * @return 创建的提现订单
       */
    @PostMapping("/withdraw")
    public Result<WalletOrder> withdraw(HttpServletRequest request,
                                        @RequestBody WalletWithdrawDTO dto) {
        Long userId = principalResolver.resolveUserId(request);
        if (userId == null) {
            return Result.error(401, "未获取到当前账户");
        }
        if (dto.merchantId() == null) {
            return Result.error("商户ID不能为空");
        }
        if (dto.amount() == null || dto.amount().compareTo(java.math.BigDecimal.ZERO) <= 0) {
            return Result.error("提现金额必须大于0");
        }
        if (!StringUtils.hasText(dto.bankAccount())) {
            return Result.error("银行账号不能为空");
        }

          // 强校验：用户标识与操作者强制等于当前用户（record 不可变，重建实例覆盖）
        WalletWithdrawDTO effectiveDto = new WalletWithdrawDTO(
                dto.merchantId(), userId, dto.withdrawNo(), dto.notifyUrl(), dto.amount(),
                dto.bankAccount(), dto.bankName(), dto.accountName(), String.valueOf(userId), dto.remark());

        walletAccountService.freezeForWithdraw(effectiveDto);

        WithdrawRequest withdrawRequest = new WithdrawRequest(
                dto.withdrawNo(),
                dto.notifyUrl(),
                dto.merchantId(),
                userId,
                dto.amount(),
                dto.bankAccount(),
                dto.bankName(),
                dto.accountName(),
                String.valueOf(userId),
                dto.remark());

        WalletOrder order = walletOrderService.createWithdrawOrder(withdrawRequest);
        walletOrderService.markProcessing(order.getOrderNo(), null, null);
        return Result.success(order);
    }

    /**
       * 转账
       *
       * @param request 请求
       * @param dto 转账请求参数
       * @return 转账结果
       */
    @PostMapping("/transfer")
    public Result<Void> transfer(HttpServletRequest request,
                                 @RequestBody WalletTransferDTO dto) {
        Long userId = principalResolver.resolveUserId(request);
        if (userId == null) {
            return Result.error(401, "未获取到当前账户");
        }
        if (dto.merchantId() == null) {
            return Result.error("商户ID不能为空");
        }
        if (dto.toUserId() == null) {
            return Result.error("转入用户不能为空");
        }
        if (dto.amount() == null || dto.amount().compareTo(java.math.BigDecimal.ZERO) <= 0) {
            return Result.error("转账金额必须大于0");
        }
        if (userId.equals(dto.toUserId())) {
            return Result.error("不能转账给自己");
        }

        String transferNo = "TRF" + System.currentTimeMillis() + UUID.randomUUID().toString().substring(0, 6).toUpperCase();

        walletAccountService.transfer(new WalletTransferOpDTO(
                dto.merchantId(), userId, dto.toUserId(),
                dto.amount(), transferNo, String.valueOf(userId), dto.remark()));
        return Result.success();
    }
}