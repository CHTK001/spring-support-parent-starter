package com.chua.payment.support.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.chua.payment.support.channel.WithdrawRequest;
import com.chua.payment.support.common.PageResult;
import com.chua.payment.support.common.Result;
import com.chua.payment.support.entity.WalletOrder;
import com.chua.payment.support.entity.WalletWithdrawApproval;
import com.chua.payment.support.service.WalletAccountService;
import com.chua.payment.support.service.WalletOrderService;
import com.chua.payment.support.service.WalletWithdrawApprovalService;
import com.chua.payment.support.support.PaymentAccountPrincipalResolver;
import com.chua.starter.oauth.client.support.user.UserResume;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 钱包提现审批控制器（管理员端）
 */
@RestController
@RequestMapping("/wallet/withdraw-approval")
@RequiredArgsConstructor
public class WalletWithdrawApprovalController {

    private final WalletWithdrawApprovalService walletWithdrawApprovalService;
    private final WalletAccountService walletAccountService;
    private final WalletOrderService walletOrderService;
    private final PaymentAccountPrincipalResolver principalResolver;

    @GetMapping("/page")
    public Result<PageResult<WalletWithdrawApproval>> page(HttpServletRequest request,
                                                            @RequestParam(defaultValue = "1") int pageNum,
                                                            @RequestParam(defaultValue = "10") int pageSize,
                                                            @RequestParam(required = false) String status) {
        Result<PageResult<WalletWithdrawApproval>> denied = denyIfNecessary(request);
        if (denied != null) return denied;

        Page<WalletWithdrawApproval> page = walletWithdrawApprovalService.pageApprovals(pageNum, pageSize, status);
        return Result.success(PageResult.of(page));
    }

    @PostMapping("/{id}/approve")
    public Result<String> approve(@PathVariable Long id, HttpServletRequest request) {
        Result<String> denied = denyIfNecessary(request);
        if (denied != null) return denied;

        String operator = resolveOperator(request);
        WalletWithdrawApproval approval = walletWithdrawApprovalService.getById(id);

        // 扣除冻结余额
        walletAccountService.confirmWithdraw(approval.getMerchantId(), approval.getUserId(),
                approval.getWithdrawNo(), approval.getAmount());

        // 创建提现订单并标记处理中
        WithdrawRequest withdrawRequest = new WithdrawRequest();
        withdrawRequest.setWithdrawNo(approval.getWithdrawNo());
        withdrawRequest.setMerchantId(approval.getMerchantId());
        withdrawRequest.setUserId(approval.getUserId());
        withdrawRequest.setAmount(approval.getAmount());
        withdrawRequest.setBankAccount(approval.getBankAccount());
        withdrawRequest.setBankName(approval.getBankName());
        withdrawRequest.setAccountName(approval.getAccountName());
        withdrawRequest.setOperator(operator);
        withdrawRequest.setRemark(approval.getRemark());

        WalletOrder order = walletOrderService.createWithdrawOrder(withdrawRequest);
        walletOrderService.markProcessing(order.getOrderNo(), null, null);

        // 更新审批状态为已通过
        walletWithdrawApprovalService.approve(id, operator);

        return Result.success("审批通过，提现订单已创建");
    }

    @PostMapping("/{id}/reject")
    public Result<String> reject(@PathVariable Long id,
                                 @RequestParam(required = false) String reason,
                                 HttpServletRequest request) {
        Result<String> denied = denyIfNecessary(request);
        if (denied != null) return denied;

        String operator = resolveOperator(request);
        WalletWithdrawApproval approval = walletWithdrawApprovalService.getById(id);

        // 解冻余额（frozen → available）
        walletAccountService.cancelWithdraw(approval.getMerchantId(), approval.getUserId(),
                approval.getWithdrawNo(), approval.getAmount(), reason);

        // 更新审批状态为已拒绝
        walletWithdrawApprovalService.reject(id, operator, reason);

        return Result.success("已拒绝提现申请");
    }

    /**
     * 提现到账确认（厂商回调成功）
     * @param orderNo 钱包订单号
     * @param thirdPartyOrderNo 厂商订单号
     * @param responsePayload 厂商响应报文
     */
    @PostMapping("/{orderNo}/withdrawal/success")
    public Result<String> withdrawalSuccess(@PathVariable String orderNo,
                                             @RequestParam(required = false) String thirdPartyOrderNo,
                                             @RequestParam(required = false) String responsePayload,
                                             HttpServletRequest request) {
        Result<String> denied = denyIfNecessary(request);
        if (denied != null) return denied;

        WalletOrder order = walletOrderService.getByOrderNo(orderNo);
        if (order == null) {
            return Result.error("钱包订单不存在");
        }
        if (!"PROCESSING".equals(order.getStatus())) {
            return Result.error("仅处理中的订单可以确认到账");
        }

        walletOrderService.markSuccess(orderNo, thirdPartyOrderNo, responsePayload);
        return Result.success("提现到账已确认");
    }

    /**
     * 提现失败回调（厂商处理失败，审核通过但提现未到账）
     * @param orderNo 钱包订单号
     * @param responsePayload 厂商响应报文
     * @param reason 失败原因
     */
    @PostMapping("/{orderNo}/withdrawal/fail")
    public Result<String> withdrawalFail(@PathVariable String orderNo,
                                          @RequestParam(required = false) String responsePayload,
                                          @RequestParam(required = false) String reason,
                                          HttpServletRequest request) {
        Result<String> denied = denyIfNecessary(request);
        if (denied != null) return denied;

        WalletOrder order = walletOrderService.getByOrderNo(orderNo);
        if (order == null) {
            return Result.error("钱包订单不存在");
        }
        final String failReason = StringUtils.hasText(reason) ? reason : "厂商处理失败";
        walletOrderService.markFailed(orderNo, responsePayload, failReason);
        return Result.success("提现失败已记录");
    }

    private <T> Result<T> denyIfNecessary(HttpServletRequest request) {
        UserResume userResume = principalResolver.resolve(request);
        if (userResume == null) {
            return Result.error(401, "未获取到当前账号");
        }
        if (!principalResolver.isAdmin(userResume)) {
            return Result.error(403, "仅超管和管理员可访问");
        }
        return null;
    }

    private String resolveOperator(HttpServletRequest request) {
        UserResume userResume = principalResolver.resolve(request);
        if (userResume == null) return "system";
        return String.valueOf(userResume.getUserId());
    }
}
