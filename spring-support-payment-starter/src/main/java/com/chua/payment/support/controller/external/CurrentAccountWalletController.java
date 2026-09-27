package com.chua.payment.support.controller.external;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.chua.payment.support.channel.WithdrawRequest;
import com.chua.payment.support.common.PageResult;
import com.chua.payment.support.common.Result;
import com.chua.payment.support.dto.CreateApprovalDTO;
import com.chua.payment.support.dto.WalletCancelWithdrawRequest;
import com.chua.payment.support.dto.WalletLogQueryRequest;
import com.chua.payment.support.dto.WalletOrderQueryRequest;
import com.chua.payment.support.dto.WalletTransferOpDTO;
import com.chua.payment.support.dto.WalletTransferDTO;
import com.chua.payment.support.dto.WalletWithdrawDTO;
import com.chua.payment.support.entity.PaymentGlobalConfig;
import com.chua.payment.support.entity.PaymentOrder;
import com.chua.payment.support.entity.WalletAccountLog;
import com.chua.payment.support.entity.WalletOrder;
import com.chua.payment.support.entity.WalletWithdrawApproval;
import com.chua.payment.support.mapper.PaymentGlobalConfigMapper;
import com.chua.payment.support.mapper.PaymentOrderMapper;
import com.chua.payment.support.service.WalletAccountService;
import com.chua.payment.support.service.WalletOrderService;
import com.chua.payment.support.service.WalletWithdrawApprovalService;
import com.chua.payment.support.support.PaymentAccountPrincipalResolver;
import com.chua.payment.support.vo.WalletAccountVO;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.UUID;

/**
 * 当前用户钱包自助 API
 * <p>所有接口均校验当前用户身份，userId 从认证上下文获取，不允许客户端传入。
 *
 * @author CH
 * @since 4.0.0
 */
@RestController
@RequestMapping("/account/wallet")
@RequiredArgsConstructor
public class CurrentAccountWalletController {

    /**
     * 钱包账户服务
     */
    private final WalletAccountService walletAccountService;
    /**
     * 钱包订单服务
     */
    private final WalletOrderService walletOrderService;
    /**
     * 钱包提现审批服务
     */
    private final WalletWithdrawApprovalService walletWithdrawApprovalService;
    /**
     * 支付全局配置映射
     */
    private final PaymentGlobalConfigMapper paymentGlobalConfigMapper;
    /**
     * 支付订单映射器
     */
    private final PaymentOrderMapper paymentOrderMapper;
    /**
     * 登录主体解析器
     */
    private final PaymentAccountPrincipalResolver principalResolver;

    @GetMapping
    /**
     * 获取钱包
     * @param request 请求
     * @param merchantId merchantid
     */
    public Result<WalletAccountVO> getWallet(HttpServletRequest request,
                                             @RequestParam Long merchantId) {
        Long userId = principalResolver.resolveUserId(request);
        if (userId == null) {
            return Result.error(401, "未获取到当前账户");
        }
        return Result.success(walletAccountService.getAccount(merchantId, userId));
    }

    @GetMapping("/log/page")
    /**
     * 流水列表
     * @param request 请求
     * @param pageNum pagenum
     * @param pageSize page大小
     * @param merchantId merchantid
     * @param bizType 业务类型
     * @param bizNo 业务单号
     */
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
        Page<WalletAccountLog> page = walletAccountService.pageLogs(new WalletLogQueryRequest(pageNum, pageSize, merchantId, userId, bizType, bizNo, null, null));
        return Result.success(PageResult.of(page));
    }

    @GetMapping("/order/page")
    /**
     * 订单列表
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
            return Result.error(401, "未获取到当前账户");
        }
        Page<WalletOrder> page = walletOrderService.page(new WalletOrderQueryRequest(pageNum, pageSize, merchantId, userId, orderType, status));
        return Result.success(PageResult.of(page));
    }

    @PostMapping("/withdraw")
    /**
     * 提现
     * @param request 请求
     * @param dto dto
     */
    public Result<WalletWithdrawApproval> withdraw(HttpServletRequest request,
                                                    @RequestBody WalletWithdrawDTO dto) {
        Long userId = principalResolver.resolveUserId(request);
        if (userId == null) {
            return Result.error(401, "未获取到当前账户");
        }
        if (dto.getMerchantId() == null) {
            return Result.error("商户ID不能为空");
        }
        if (dto.getAmount() == null || dto.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            return Result.error("提现金额必须大于0");
        }
        if (!StringUtils.hasText(dto.getBankAccount())) {
            return Result.error("银行账号不能为空");
        }

        // 强校验：用户标识与操作者强制等于当前用户
        dto.setUserId(userId);
        dto.setOperator(String.valueOf(userId));

        // 优惠券抵扣提现校验
        PaymentGlobalConfig globalConfig = paymentGlobalConfigMapper.selectOne(
                new LambdaQueryWrapper<PaymentGlobalConfig>()
                        .eq(PaymentGlobalConfig::getConfigKey, "DEFAULT")
                        .last("limit 1"));
        if (globalConfig != null && Boolean.FALSE.equals(globalConfig.getCouponDeductWithdrawable())) {
            List<PaymentOrder> orders = paymentOrderMapper.selectList(
                    new LambdaQueryWrapper<PaymentOrder>()
                            .eq(PaymentOrder::getMerchantId, dto.getMerchantId())
                            .eq(PaymentOrder::getUserId, userId)
                            .isNotNull(PaymentOrder::getDiscountAmount));
            BigDecimal totalCouponDiscount = orders.stream()
                    .map(o -> o.getDiscountAmount() != null ? o.getDiscountAmount() : BigDecimal.ZERO)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            WalletAccountVO account = walletAccountService.getAccount(dto.getMerchantId(), userId);
            BigDecimal maxWithdrawable = account.getAvailableBalance().subtract(totalCouponDiscount);
            if (maxWithdrawable.compareTo(BigDecimal.ZERO) < 0) {
                maxWithdrawable = BigDecimal.ZERO;
            }
            if (dto.getAmount().compareTo(maxWithdrawable) > 0) {
                return Result.error("优惠券抵扣部分不可提现，可提现金额为：" + maxWithdrawable.setScale(2, RoundingMode.HALF_UP));
            }
        }

        // 生成提现流水号
        String withdrawNo = "WDW" + System.currentTimeMillis() + UUID.randomUUID().toString().substring(0, 6).toUpperCase();
        dto.setWithdrawNo(withdrawNo);

        // 冻结余额（frozen 可用）
        walletAccountService.freezeForWithdraw(dto);

        // 创建提现审批记录（PENDING），不再直接创建订单
        WalletWithdrawApproval approval = walletWithdrawApprovalService.createApproval(
                new CreateApprovalDTO(
                        dto.getMerchantId(), userId, dto.getAmount(),
                        dto.getBankAccount(), dto.getBankName(), dto.getAccountName(),
                        withdrawNo, dto.getRemark()));

        return Result.success(approval);
    }

    /**
     * 用户主动取消提现申请（仅 PENDING 状态可取消）
     *
     * @param approvalId 审批标识
     * @param reason 取消原因
     * @param request 请求
     */
    @PostMapping("/withdraw/{approvalId}/cancel")
    public Result<Void> cancelWithdraw(@PathVariable Long approvalId,
                                       @RequestParam(required = false) String reason,
                                       HttpServletRequest request) {
        Long userId = principalResolver.resolveUserId(request);
        if (userId == null) {
            return Result.error(401, "未获取到当前账户");
        }

        WalletWithdrawApproval approval = walletWithdrawApprovalService.getById(approvalId);
        if (!userId.equals(approval.getUserId())) {
            return Result.error(403, "只能取消自己的提现申请");
        }
        if (!"PENDING".equals(approval.getStatus())) {
            return Result.error("仅待审批的提现可以取消");
        }

        // 解冻余额（frozen 可用）
        walletAccountService.cancelWithdraw(new WalletCancelWithdrawRequest(
                approval.getMerchantId(), approval.getUserId(),
                approval.getWithdrawNo(), approval.getAmount(), reason));

        // 标记审批为已拒绝（用户主动取消）
        walletWithdrawApprovalService.reject(approvalId, String.valueOf(userId),
                "用户主动取消" + (StringUtils.hasText(reason) ? ": " + reason : ""));

        return Result.success();
    }

    @PostMapping("/transfer")
    /**
     * 转账
     * @param request 请求
     * @param dto dto
     */
    public Result<Void> transfer(HttpServletRequest request,
                                 @RequestBody WalletTransferDTO dto) {
        Long userId = principalResolver.resolveUserId(request);
        if (userId == null) {
            return Result.error(401, "未获取到当前账户");
        }
        if (dto.getMerchantId() == null) {
            return Result.error("商户ID不能为空");
        }
        if (dto.getToUserId() == null) {
            return Result.error("转入用户不能为空");
        }
        if (dto.getAmount() == null || dto.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            return Result.error("转账金额必须大于0");
        }
        if (userId.equals(dto.getToUserId())) {
            return Result.error("不能转账给自己");
        }

        // 强校验：从用户标识推导操作者，强制等于当前用户
        String transferNo = "TRF" + System.currentTimeMillis() + UUID.randomUUID().toString().substring(0, 6).toUpperCase();

        walletAccountService.transfer(new WalletTransferOpDTO(
                dto.getMerchantId(), userId, dto.getToUserId(),
                dto.getAmount(), transferNo, String.valueOf(userId), dto.getRemark()));
        return Result.success();
    }
}
