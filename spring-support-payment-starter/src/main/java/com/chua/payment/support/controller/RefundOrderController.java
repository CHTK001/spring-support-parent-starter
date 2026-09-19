package com.chua.payment.support.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.chua.payment.support.common.PageResult;
import com.chua.payment.support.common.Result;
import com.chua.payment.support.dto.RefundOrderQueryRequest;
import com.chua.payment.support.dto.RefundOperateDTO;
import com.chua.payment.support.dto.RefundSuccessDTO;
import com.chua.payment.support.entity.PaymentRefundOrder;
import com.chua.payment.support.service.PaymentOrderService;
import com.chua.payment.support.service.PaymentRefundOrderService;
import com.chua.payment.support.vo.RefundOrderVO;
import com.chua.starter.common.support.annotations.Permission;
import com.chua.starter.oauth.client.support.contants.AuthConstant;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 退款单管理控制器
 * @author CH
 * @since 4.0.0
 */
@Tag(name = "退款管理", description = "支付退款单相关接口")
@RestController
@RequestMapping("/refund")
@RequiredArgsConstructor
public class RefundOrderController {

    /** Paymentrefund排序服务 */
    private final PaymentRefundOrderService paymentRefundOrderService;
    /** Payment排序服务 */
    private final PaymentOrderService paymentOrderService;

    @Operation(summary = "查询退款单")
    @GetMapping("/{id}")
    @Permission(value = "payment:refund:view", role = {AuthConstant.ADMIN, AuthConstant.SUPER_ADMIN, AuthConstant.OPS})
    /**
     * 获取byid
     *
     * @param id 标识
     * @return 获取byid的结果
     */
    public Result<RefundOrderVO> getById(@PathVariable Long id) {
        return Result.success(paymentRefundOrderService.getDetail(id));
    }

    @Operation(summary = "查询订单退款单列表")
    @GetMapping("/order/{orderId}")
    @Permission(value = "payment:refund:view", role = {AuthConstant.ADMIN, AuthConstant.SUPER_ADMIN, AuthConstant.OPS})
    /**
     * 列表by订单标识
     *
     * @param orderId 订单标识
     * @return 列表by订单id的结果
     */
    public Result<List<RefundOrderVO>> listByOrderId(@PathVariable Long orderId) {
        return Result.success(paymentRefundOrderService.listVoByOrderId(orderId));
    }

    @Operation(summary = "分页查询退款单")
    @GetMapping("/page")
    @Permission(value = "payment:refund:view", role = {AuthConstant.ADMIN, AuthConstant.SUPER_ADMIN, AuthConstant.OPS})
    /**
     * Page
     *
     * @param "1" "1"
     * @return page的结果
     */
    public Result<PageResult<RefundOrderVO>> page(@RequestParam(defaultValue = "1") int pageNum,
                                                  @RequestParam(defaultValue = "10") int pageSize,
                                                  @RequestParam(required = false) Long merchantId,
                                                  @RequestParam(required = false) String orderNo,
                                                  @RequestParam(required = false) String refundNo,
                                                  @RequestParam(required = false) String status) {
        Page<RefundOrderVO> page = paymentRefundOrderService.page(new RefundOrderQueryRequest(pageNum, pageSize, merchantId, orderNo, refundNo, status));
        return Result.success(PageResult.of(page));
    }

    @Operation(summary = "标记退款成功")
    @PutMapping("/{id}/success")
    @Permission(value = "payment:refund:success", role = {AuthConstant.ADMIN, AuthConstant.SUPER_ADMIN})
    /**
     * mark退税成功
     * @param id 标识
     * @param dto dto
     */
    public Result<Boolean> markRefundSuccess(@PathVariable Long id,
                                             @RequestBody(required = false) RefundOperateDTO dto) {
        PaymentRefundOrder refundOrder = paymentRefundOrderService.getById(id);
        return Result.success(paymentOrderService.refundSuccess(new RefundSuccessDTO(
                refundOrder.getRefundNo(),
                dto != null ? dto.getRefundAmount() : null,
                dto != null ? dto.getThirdPartyRefundNo() : null,
                dto != null ? dto.getOperator() : null,
                null,
                dto != null ? dto.getRemark() : null)));
    }

    @Operation(summary = "标记退款失败")
    @PutMapping("/{id}/fail")
    @Permission(value = "payment:refund:fail", role = {AuthConstant.ADMIN, AuthConstant.SUPER_ADMIN})
    /**
     * mark退税失败
     * @param id 标识
     * @param dto dto
     */
    public Result<Boolean> markRefundFail(@PathVariable Long id,
                                          @RequestBody(required = false) RefundOperateDTO dto) {
        PaymentRefundOrder refundOrder = paymentRefundOrderService.getById(id);
        return Result.success(paymentOrderService.refundFail(
                refundOrder.getRefundNo(),
                null,
                dto != null ? dto.getOperator() : null,
                dto != null ? dto.getRemark() : null));
    }
}

