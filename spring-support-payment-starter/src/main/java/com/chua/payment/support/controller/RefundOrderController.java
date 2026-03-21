package com.chua.payment.support.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.chua.common.support.lang.code.ReturnResult;
import com.chua.payment.support.common.PageResult;
import com.chua.payment.support.dto.RefundOperateDTO;
import com.chua.payment.support.entity.PaymentRefundOrder;
import com.chua.payment.support.service.PaymentOrderService;
import com.chua.payment.support.service.PaymentRefundOrderService;
import com.chua.payment.support.vo.RefundOrderVO;
import com.chua.starter.common.support.annotations.Permission;
import com.chua.starter.oauth.client.support.contants.AuthConstant;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 退款单管理控制器
 */
@Tag(name = "退款管理", description = "支付退款单相关接口")
@RestController
@RequestMapping("/api/refund")
@RequiredArgsConstructor
public class RefundOrderController {

    private final PaymentRefundOrderService paymentRefundOrderService;
    private final PaymentOrderService paymentOrderService;

    @Operation(summary = "查询退款单")
    @GetMapping("/{id}")
    @Permission(value = "payment:refund:view", role = {AuthConstant.ADMIN, AuthConstant.SUPER_ADMIN, AuthConstant.OPS})
    public ReturnResult<RefundOrderVO> getById(@PathVariable Long id) {
        return ReturnResult.success(paymentRefundOrderService.getDetail(id));
    }

    @Operation(summary = "查询订单退款单列表")
    @GetMapping("/order/{orderId}")
    @Permission(value = "payment:refund:view", role = {AuthConstant.ADMIN, AuthConstant.SUPER_ADMIN, AuthConstant.OPS})
    public ReturnResult<List<RefundOrderVO>> listByOrderId(@PathVariable Long orderId) {
        return ReturnResult.success(paymentRefundOrderService.listVoByOrderId(orderId));
    }

    @Operation(summary = "分页查询退款单")
    @GetMapping("/page")
    @Permission(value = "payment:refund:view", role = {AuthConstant.ADMIN, AuthConstant.SUPER_ADMIN, AuthConstant.OPS})
    public ReturnResult<PageResult<RefundOrderVO>> page(@RequestParam(defaultValue = "1") int pageNum,
                                                         @RequestParam(defaultValue = "10") int pageSize,
                                                         @RequestParam(required = false) Long merchantId,
                                                         @RequestParam(required = false) String orderNo,
                                                         @RequestParam(required = false) String refundNo,
                                                         @RequestParam(required = false) String status) {
        Page<RefundOrderVO> page = paymentRefundOrderService.page(pageNum, pageSize, merchantId, orderNo, refundNo, status);
        return ReturnResult.success(PageResult.of(page));
    }

    @Operation(summary = "标记退款成功")
    @PutMapping("/{id}/success")
    @Permission(value = "payment:refund:success", role = {AuthConstant.ADMIN, AuthConstant.SUPER_ADMIN})
    public ReturnResult<Boolean> markRefundSuccess(@PathVariable Long id,
                                                   @RequestBody(required = false) RefundOperateDTO dto) {
        PaymentRefundOrder refundOrder = paymentRefundOrderService.getById(id);
        return ReturnResult.success(paymentOrderService.refundSuccess(
                refundOrder.getRefundNo(),
                dto != null ? dto.getRefundAmount() : null,
                dto != null ? dto.getThirdPartyRefundNo() : null,
                dto != null ? dto.getOperator() : null,
                null,
                dto != null ? dto.getRemark() : null));
    }

    @Operation(summary = "标记退款失败")
    @PutMapping("/{id}/fail")
    @Permission(value = "payment:refund:fail", role = {AuthConstant.ADMIN, AuthConstant.SUPER_ADMIN})
    public ReturnResult<Boolean> markRefundFail(@PathVariable Long id,
                                                @RequestBody(required = false) RefundOperateDTO dto) {
        PaymentRefundOrder refundOrder = paymentRefundOrderService.getById(id);
        return ReturnResult.success(paymentOrderService.refundFail(
                refundOrder.getRefundNo(),
                null,
                dto != null ? dto.getOperator() : null,
                dto != null ? dto.getRemark() : null));
    }
}
