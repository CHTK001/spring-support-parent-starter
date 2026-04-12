package com.chua.payment.support.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.chua.payment.support.common.PageResult;
import com.chua.payment.support.dto.PaymentSchedulerTaskUpdateDTO;
import com.chua.payment.support.entity.PaymentNotifyError;
import com.chua.payment.support.entity.PaymentNotifyLog;
import com.chua.payment.support.mapper.PaymentNotifyErrorMapper;
import com.chua.payment.support.mapper.PaymentNotifyLogMapper;
import com.chua.payment.support.service.PaymentDashboardService;
import com.chua.payment.support.service.PaymentNotifyProcessService;
import com.chua.payment.support.service.PaymentSchedulerTaskOpsService;
import com.chua.payment.support.vo.PaymentCallbackAuditVO;
import com.chua.payment.support.vo.PaymentDashboardSummaryVO;
import com.chua.payment.support.vo.PaymentOpsOverviewVO;
import com.chua.payment.support.vo.PaymentOrderNumberStrategyVO;
import com.chua.payment.support.vo.PaymentSchedulerTaskVO;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

/**
 * 支付运营能力控制器。
 * 直接返回原始业务对象，由通用响应包装层统一输出 ReturnResult，
 * 避免这里再返回支付模块自己的 Result 造成双协议并存。
 */
@RestController
@RequestMapping("/api/ops")
@ConditionalOnProperty(prefix = "plugin.payment.ops", name = "enabled", havingValue = "true", matchIfMissing = true)
public class PaymentOpsController {

    private final PaymentSchedulerTaskOpsService paymentSchedulerTaskOpsService;
    private final PaymentDashboardService paymentDashboardService;
    private final PaymentNotifyLogMapper paymentNotifyLogMapper;
    private final PaymentNotifyErrorMapper paymentNotifyErrorMapper;
    private final PaymentNotifyProcessService paymentNotifyProcessService;

    public PaymentOpsController(PaymentSchedulerTaskOpsService paymentSchedulerTaskOpsService,
                                PaymentDashboardService paymentDashboardService,
                                PaymentNotifyLogMapper paymentNotifyLogMapper,
                                PaymentNotifyErrorMapper paymentNotifyErrorMapper,
                                PaymentNotifyProcessService paymentNotifyProcessService) {
        this.paymentSchedulerTaskOpsService = paymentSchedulerTaskOpsService;
        this.paymentDashboardService = paymentDashboardService;
        this.paymentNotifyLogMapper = paymentNotifyLogMapper;
        this.paymentNotifyErrorMapper = paymentNotifyErrorMapper;
        this.paymentNotifyProcessService = paymentNotifyProcessService;
    }

    @GetMapping("/dashboard/summary")
    public PaymentDashboardSummaryVO dashboardSummary(@RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate startDate,
                                                      @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate endDate) {
        return paymentDashboardService.summary(startDate, endDate);
    }

    @GetMapping("/overview")
    public PaymentOpsOverviewVO overview() {
        PaymentOpsOverviewVO overview = new PaymentOpsOverviewVO();
        overview.setCallbackAudits(callbackAudits());
        overview.setOrderNumberStrategies(orderNumberStrategies());
        return overview;
    }

    @GetMapping("/scheduler/tasks")
    public List<PaymentSchedulerTaskVO> listTasks() {
        return paymentSchedulerTaskOpsService.listTasks();
    }

    @PutMapping("/scheduler/tasks/{taskKey}")
    public PaymentSchedulerTaskVO updateTask(@PathVariable String taskKey,
                                             @RequestBody PaymentSchedulerTaskUpdateDTO dto) {
        return paymentSchedulerTaskOpsService.updateTask(taskKey, dto);
    }

    @PostMapping("/scheduler/tasks/{taskKey}/trigger")
    public PaymentSchedulerTaskVO triggerTask(@PathVariable String taskKey) {
        return paymentSchedulerTaskOpsService.triggerTask(taskKey);
    }

    @GetMapping("/notify/log/page")
    public PageResult<PaymentNotifyLog> pageNotifyLogs(@RequestParam(defaultValue = "1") int pageNum,
                                                       @RequestParam(defaultValue = "10") int pageSize,
                                                       @RequestParam(required = false) String notifyType,
                                                       @RequestParam(required = false) String processStatus,
                                                       @RequestParam(required = false) String orderNo,
                                                       @RequestParam(required = false) String refundNo) {
        LambdaQueryWrapper<PaymentNotifyLog> wrapper = new LambdaQueryWrapper<PaymentNotifyLog>()
                .orderByDesc(PaymentNotifyLog::getReceivedTime)
                .orderByDesc(PaymentNotifyLog::getId);
        if (StringUtils.hasText(notifyType)) {
            wrapper.eq(PaymentNotifyLog::getNotifyType, notifyType);
        }
        if (StringUtils.hasText(processStatus)) {
            wrapper.eq(PaymentNotifyLog::getProcessStatus, processStatus);
        }
        if (StringUtils.hasText(orderNo)) {
            wrapper.eq(PaymentNotifyLog::getOrderNo, orderNo);
        }
        if (StringUtils.hasText(refundNo)) {
            wrapper.eq(PaymentNotifyLog::getRefundNo, refundNo);
        }
        Page<PaymentNotifyLog> page = paymentNotifyLogMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
        return PageResult.of(page);
    }

    @GetMapping("/notify/error/page")
    public PageResult<PaymentNotifyError> pageNotifyErrors(@RequestParam(defaultValue = "1") int pageNum,
                                                           @RequestParam(defaultValue = "10") int pageSize,
                                                           @RequestParam(required = false) String notifyType,
                                                           @RequestParam(required = false) String status,
                                                           @RequestParam(required = false) String orderNo,
                                                           @RequestParam(required = false) String refundNo) {
        LambdaQueryWrapper<PaymentNotifyError> wrapper = new LambdaQueryWrapper<PaymentNotifyError>()
                .orderByDesc(PaymentNotifyError::getCreateTime)
                .orderByDesc(PaymentNotifyError::getId);
        if (StringUtils.hasText(notifyType)) {
            wrapper.eq(PaymentNotifyError::getNotifyType, notifyType);
        }
        if (StringUtils.hasText(status)) {
            wrapper.eq(PaymentNotifyError::getStatus, status);
        }
        if (StringUtils.hasText(orderNo)) {
            wrapper.eq(PaymentNotifyError::getOrderNo, orderNo);
        }
        if (StringUtils.hasText(refundNo)) {
            wrapper.eq(PaymentNotifyError::getRefundNo, refundNo);
        }
        Page<PaymentNotifyError> page = paymentNotifyErrorMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
        return PageResult.of(page);
    }

    @PostMapping("/notify/error/{id}/retry")
    public Boolean retryNotifyError(@PathVariable Long id) {
        paymentNotifyProcessService.retryFailedNotify(id);
        return true;
    }

    private List<PaymentCallbackAuditVO> callbackAudits() {
        return List.of(
                callbackAudit(
                        "WECHAT_PAY",
                        "微信支付回调",
                        "/api/notify/wechat/pay/{channelId}/{orderNo}",
                        "orderNo",
                        false,
                        "request.notifyUrl > channel.notifyUrl > merchant.defaultNotifyUrl > scoped default",
                        "默认可生成带订单号的 scoped 回调，但请求、渠道、商户级 notifyUrl 都会覆盖；控制器同时保留 /{channelId} 兼容路由。"),
                callbackAudit(
                        "ALIPAY_PAY",
                        "支付宝支付回调",
                        "/api/notify/alipay/pay/{channelId}/{orderNo}",
                        "orderNo",
                        false,
                        "request.notifyUrl > channel.notifyUrl > merchant.defaultNotifyUrl > scoped default",
                        "默认可生成带订单号的 scoped 回调，但渠道/商户/请求显式 notifyUrl 会覆盖；控制器保留 /{channelId} 兼容路由。"),
                callbackAudit(
                        "WECHAT_REFUND",
                        "微信退款回调",
                        "/api/notify/wechat/refund/{channelId}/{refundNo}",
                        "refundNo",
                        false,
                        "channel.extConfig.refundNotifyUrl > scoped default > latest refund notifyUrl",
                        "退款链路优先推荐 scoped 路由，但如果渠道 extConfig 配置了 refundNotifyUrl，仍然会覆盖成固定地址。"),
                callbackAudit(
                        "WALLET",
                        "钱包业务回调",
                        "/api/notify/wallet/{orderType}/{orderNo}",
                        "orderNo",
                        false,
                        "request.notifyUrl > scoped default",
                        "钱包回调默认按订单类型和订单号拼路由，但调用侧显式传 notifyUrl 时会覆盖。"),
                callbackAudit(
                        "WECHAT_PAYSCORE",
                        "微信支付分回调",
                        "/api/notify/wechat/payscore/{channelId}/{outOrderNo}",
                        "outOrderNo",
                        false,
                        "request.notifyUrl > channel.extConfig.payScoreNotifyUrl/paymentPointNotifyUrl > scoped default",
                        "支付分默认支持 scoped 路由，但请求或渠道 extConfig 中的支付分回调地址会覆盖。"));
    }

    private List<PaymentOrderNumberStrategyVO> orderNumberStrategies() {
        return List.of(
                orderNumberStrategy(
                        "标准支付订单",
                        "orderNo / businessOrderNo",
                        "orderNo 默认生成: ORD + 当前毫秒时间 + 6位随机大写串",
                        "businessOrderNo 可由调用方传入；未传时默认等于 orderNo",
                        "按 merchantId + businessOrderNo 做幂等复用",
                        "createOrder 时先生成内部 orderNo，再以 businessOrderNo 做唯一性检查；重复请求会返回已有活动订单。"),
                orderNumberStrategy(
                        "钱包订单",
                        "orderNo",
                        "充值/转账/提现分别生成: RCH/TRF/WDW + 当前毫秒时间 + 6位随机大写串",
                        "调用方可分别传 rechargeNo / transferNo / withdrawNo 覆盖",
                        "按 orderNo 查询现有钱包订单，存在则直接返回",
                        "钱包订单不区分 businessOrderNo，调用方如果需要幂等，应稳定传入自己的业务单号。"),
                orderNumberStrategy(
                        "微信支付分",
                        "outOrderNo",
                        "默认生成: PS + 当前毫秒时间 + 6位随机大写串",
                        "调用方可传 outOrderNo 覆盖",
                        "按 outOrderNo 唯一约束与查询幂等",
                        "支付分订单与普通支付订单分表存储，不与标准支付 orderNo 混用。"));
    }

    private PaymentCallbackAuditVO callbackAudit(String callbackType,
                                                String callbackName,
                                                String recommendedPattern,
                                                String scopedIdentifier,
                                                boolean strictScoped,
                                                String effectivePriority,
                                                String notes) {
        PaymentCallbackAuditVO vo = new PaymentCallbackAuditVO();
        vo.setCallbackType(callbackType);
        vo.setCallbackName(callbackName);
        vo.setRecommendedPattern(recommendedPattern);
        vo.setScopedIdentifier(scopedIdentifier);
        vo.setStrictScoped(strictScoped);
        vo.setEffectivePriority(effectivePriority);
        vo.setNotes(notes);
        return vo;
    }

    private PaymentOrderNumberStrategyVO orderNumberStrategy(String businessType,
                                                             String fieldName,
                                                             String generationRule,
                                                             String callerOverrideField,
                                                             String idempotentRule,
                                                             String notes) {
        PaymentOrderNumberStrategyVO vo = new PaymentOrderNumberStrategyVO();
        vo.setBusinessType(businessType);
        vo.setFieldName(fieldName);
        vo.setGenerationRule(generationRule);
        vo.setCallerOverrideField(callerOverrideField);
        vo.setIdempotentRule(idempotentRule);
        vo.setNotes(notes);
        return vo;
    }
}
