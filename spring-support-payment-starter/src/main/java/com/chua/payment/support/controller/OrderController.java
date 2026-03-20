package com.chua.payment.support.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.chua.payment.support.channel.PaymentResult;
import com.chua.payment.support.common.PageResult;
import com.chua.payment.support.common.Result;
import com.chua.payment.support.dto.OrderCreateDTO;
import com.chua.payment.support.dto.OrderOperateDTO;
import com.chua.payment.support.dto.OrderPayDTO;
import com.chua.payment.support.dto.RefundApplyDTO;
import com.chua.payment.support.service.PaymentOrderService;
import com.chua.payment.support.vo.OrderStateLogVO;
import com.chua.payment.support.vo.OrderVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 订单管理控制器
 */
@Tag(name = "订单管理", description = "支付订单相关接口")
@RestController
@RequestMapping("/api/order")
@RequiredArgsConstructor
public class OrderController {

    private final PaymentOrderService orderService;

    @Operation(summary = "创建订单")
    @PostMapping
    public Result<OrderVO> createOrder(@RequestBody OrderCreateDTO dto) {
        return Result.success(orderService.createOrder(dto));
    }

    @Operation(summary = "查询订单")
    @GetMapping("/{id}")
    public Result<OrderVO> getOrder(@PathVariable Long id) {
        return Result.success(orderService.getOrder(id));
    }

    @Operation(summary = "分页查询订单")
    @GetMapping("/list")
    public Result<PageResult<OrderVO>> listOrders(@RequestParam(defaultValue = "1") int page,
                                                  @RequestParam(defaultValue = "10") int size,
                                                  @RequestParam(required = false) Long merchantId,
                                                  @RequestParam(required = false) String orderNo,
                                                  @RequestParam(required = false) String status) {
        Page<OrderVO> pageResult = orderService.listOrders(page, size, merchantId, orderNo, status);
        return Result.success(PageResult.of(pageResult));
    }

    @Operation(summary = "查询商户订单")
    @GetMapping("/merchant/{merchantId}")
    public Result<PageResult<OrderVO>> listMerchantOrders(@PathVariable Long merchantId,
                                                          @RequestParam(defaultValue = "1") int page,
                                                          @RequestParam(defaultValue = "10") int size,
                                                          @RequestParam(required = false) String status) {
        return Result.success(PageResult.of(orderService.listMerchantOrders(merchantId, page, size, status)));
    }

    @Operation(summary = "查询订单状态流转日志")
    @GetMapping("/{id}/logs")
    public Result<List<OrderStateLogVO>> listOrderLogs(@PathVariable Long id) {
        return Result.success(orderService.listOrderLogs(id));
    }

    @Operation(summary = "真实发起支付")
    @PostMapping("/{id}/pay")
    public Result<PaymentResult> payOrder(@PathVariable Long id,
                                          @RequestBody(required = false) OrderPayDTO dto) {
        return Result.success(orderService.payOrder(id, dto));
    }

    @Operation(summary = "同步第三方支付状态")
    @PostMapping("/{id}/sync")
    public Result<OrderVO> syncOrder(@PathVariable Long id) {
        return Result.success(orderService.syncOrder(id));
    }

    @Operation(summary = "发起支付")
    @PutMapping("/{id}/pay-start")
    public Result<Boolean> startPay(@PathVariable Long id,
                                    @RequestBody(required = false) OrderOperateDTO dto) {
        return Result.success(orderService.startPay(id, dto != null ? dto.getOperator() : null));
    }

    @Operation(summary = "标记支付成功")
    @PutMapping("/{id}/pay-success")
    public Result<Boolean> paySuccess(@PathVariable Long id,
                                      @RequestBody(required = false) OrderOperateDTO dto) {
        return Result.success(orderService.paySuccess(
                id,
                dto != null ? dto.getPaidAmount() : null,
                dto != null ? dto.getThirdPartyOrderNo() : null,
                dto != null ? dto.getOperator() : null));
    }

    @Operation(summary = "标记支付失败")
    @PutMapping("/{id}/pay-fail")
    public Result<Boolean> payFail(@PathVariable Long id,
                                   @RequestBody(required = false) OrderOperateDTO dto) {
        return Result.success(orderService.payFail(id, dto != null ? dto.getRemark() : null, dto != null ? dto.getOperator() : null));
    }

    @Operation(summary = "取消订单")
    @PutMapping("/{id}/cancel")
    public Result<Boolean> cancelOrder(@PathVariable Long id,
                                       @RequestBody(required = false) OrderOperateDTO dto) {
        return Result.success(orderService.cancelOrder(id, dto != null ? dto.getOperator() : null, dto != null ? dto.getRemark() : null));
    }

    @Operation(summary = "完成订单")
    @PutMapping("/{id}/complete")
    public Result<Boolean> completeOrder(@PathVariable Long id,
                                         @RequestBody(required = false) OrderOperateDTO dto) {
        return Result.success(orderService.completeOrder(id, dto != null ? dto.getOperator() : null));
    }

    @Operation(summary = "申请退款")
    @PostMapping("/{id}/refund")
    public Result<Boolean> refundOrder(@PathVariable Long id,
                                       @RequestBody RefundApplyDTO dto) {
        return Result.success(orderService.refundOrder(id, dto));
    }

    @Operation(summary = "标记退款成功")
    @PutMapping("/{id}/refund-success")
    public Result<Boolean> refundSuccess(@PathVariable Long id,
                                         @RequestBody(required = false) OrderOperateDTO dto) {
        return Result.success(orderService.refundSuccess(id, dto != null ? dto.getRefundAmount() : null, dto != null ? dto.getOperator() : null));
    }

    @Operation(summary = "标记退款失败")
    @PutMapping("/{id}/refund-fail")
    public Result<Boolean> refundFail(@PathVariable Long id,
                                      @RequestBody(required = false) OrderOperateDTO dto) {
        return Result.success(orderService.refundFail(id, dto != null ? dto.getRemark() : null, dto != null ? dto.getOperator() : null));
    }

    @Operation(summary = "删除订单")
    @DeleteMapping("/{id}")
    public Result<Boolean> deleteOrder(@PathVariable Long id) {
        return Result.success(orderService.deleteOrder(id));
    }
}
