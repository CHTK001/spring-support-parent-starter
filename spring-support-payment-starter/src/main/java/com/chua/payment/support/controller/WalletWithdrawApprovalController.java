package com.chua.payment.support.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.chua.payment.support.channel.WithdrawRequest;
import com.chua.payment.support.common.PageResult;
import com.chua.payment.support.common.Result;
import com.chua.payment.support.dto.WalletCancelWithdrawRequest;
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
 * @author CH
 * @since 4.0.0
 */
@RestController
@RequestMapping("/wallet/withdraw-approval")
@RequiredArgsConstructor
public class WalletWithdrawApprovalController {

    /**
     * 钱包提现审批服务
     */
    private final WalletWithdrawApprovalService walletWithdrawApprovalService;
    /**
     * 钱包账户服务
     */
    private final WalletAccountService walletAccountService;
    /**
     * 钱包排序服务
     */
    private final WalletOrderService walletOrderService;
    /**
     * 登录主体解析器
     */
    private final PaymentAccountPrincipalResolver principalResolver;

    @GetMapping("/page")
    /**
     * 分页
     * @param request 请求
     * @param pageNum pagenum
     * @param pageSize page大小
     * @param status 状态
     */
    public Result<PageResult<WalletWithdrawApproval>> page(HttpServletRequest request,
                                                            @RequestParam(defaultValue = "1") int pageNum,
                                                            @RequestParam(defaultValue = "10") int pageSize,
                                                            @RequestParam(required = false) String status) {
        Result<PageResult<WalletWithdrawApproval>> denied = denyIfNecessary(request);
        if (denied != null) {
            return denied;
        }

        Page<WalletWithdrawApproval> page = walletWithdrawApprovalService.pageApprovals(pageNum, pageSize, status);
        return Result.success(PageResult.of(page));
    }

    @PostMapping("/{id}/approve")
    /**
     * 审批通过
     * @param id 标识
     * @param request 请求
     * @return 返回值
    */
    public Result<String> approve(@PathVariable Long id, HttpServletRequest request) {
        Result<String> denied = denyIfNecessary(request);
        if (denied != null) {
            return denied;
        }

        String operator = resolveOperator(request);
        WalletWithdrawApproval approval = walletWithdrawApprovalService.getById(id);

        // 扣除冻结余额
        walletAccountService.confirmWithdraw(approval.getMerchantId(), approval.getUserId(),
                approval.getWithdrawNo(), approval.getAmount());

        // 创建提现订单并标记处理中
        WithdrawRequest withdrawRequest = new WithdrawRequest(
                approval.getWithdrawNo(),
                null,
                approval.getMerchantId(),
                approval.getUserId(),
                approval.getAmount(),
                approval.getBankAccount(),
                approval.getBankName(),
                approval.getAccountName(),
                operator,
                approval.getRemark());

        WalletOrder order = walletOrderService.createWithdrawOrder(withdrawRequest);
        walletOrderService.markProcessing(order.getOrderNo(), null, null);

        // 更新审批状态为已通过
        walletWithdrawApprovalService.approve(id, operator);

        return Result.success("审批通过，提现订单已创建");
    }

    @PostMapping("/{id}/reject")
    /**
     * 审批拒绝
     * @param id 标识
     * @param reason 拒绝原因
     * @param request 请求
     */
    public Result<String> reject(@PathVariable Long id,
                                 @RequestParam(required = false) String reason,
                                 HttpServletRequest request) {
        Result<String> denied = denyIfNecessary(request);
        if (denied != null) {
            return denied;
        }

        String operator = resolveOperator(request);
        WalletWithdrawApproval approval = walletWithdrawApprovalService.getById(id);

        // 解冻余额（frozen 可用）
        walletAccountService.cancelWithdraw(new WalletCancelWithdrawRequest(
                approval.getMerchantId(), approval.getUserId(),
                approval.getWithdrawNo(), approval.getAmount(), reason));

        // 更新审批状态为已拒绝
        walletWithdrawApprovalService.reject(id, operator, reason);

        return Result.success("已拒绝提现申请");
    }

    /**
     * 提现到账确认（商户回調成功）
     * @param orderNo 钱包订单号
     * @param thirdPartyOrderNo 第三方订单号
     * @param responsePayload 第三方响应报文
     */
    @PostMapping("/{orderNo}/withdrawal/success")
    public Result<String> withdrawalSuccess(@PathVariable String orderNo,
                                             @RequestParam(required = false) String thirdPartyOrderNo,
                                             @RequestParam(required = false) String responsePayload,
                                             HttpServletRequest request) {
        Result<String> denied = denyIfNecessary(request);
        if (denied != null) {
            return denied;
        }

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
     * 提现失败回调（商户处理失败，审核通过但提现未到账）
     * @param orderNo 钱包订单号
     * @param responsePayload 第三方响应报文
     * @param reason 失败原因
     */
    @PostMapping("/{orderNo}/withdrawal/fail")
    public Result<String> withdrawalFail(@PathVariable String orderNo,
                                          @RequestParam(required = false) String responsePayload,
                                          @RequestParam(required = false) String reason,
                                          HttpServletRequest request) {
        Result<String> denied = denyIfNecessary(request);
        if (denied != null) {
            return denied;
        }

        WalletOrder order = walletOrderService.getByOrderNo(orderNo);
        if (order == null) {
            return Result.error("钱包订单不存在");
        }
        final String failReason = StringUtils.hasText(reason) ? reason : "商户处理失败";
        walletOrderService.markFailed(orderNo, responsePayload, failReason);
        return Result.success("提现失败已记录");
    }

    /**
     * 权限校验
     *
     * @param request 请求
     */
    private <T> Result<T> denyIfNecessary(HttpServletRequest request) {
        UserResume userResume = principalResolver.resolve(request);
        if (userResume == null) {
            return Result.error(401, "未获取到当前账户");
        }
        if (!principalResolver.isAdmin(userResume)) {
            return Result.error(403, "仅超管和管理员可访问");
        }
        return null;
    }

    /**
     * 解析操作人
     *
     * @param request 请求
     * @return resolve操作者标识的结果
     */
    private String resolveOperator(HttpServletRequest request) {
        UserResume userResume = principalResolver.resolve(request);
        if (userResume == null) {
            return "system";
        }
        return String.valueOf(userResume.getUserId());
    }
}
