package com.chua.payment.support.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.chua.payment.support.common.PageResult;
import com.chua.payment.support.common.Result;
import com.chua.payment.support.dto.OrderCreateDTO;
import com.chua.payment.support.service.PaymentOrderService;
import com.chua.payment.support.vo.OrderVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 订单管理控制器
 *
 * @author CH
 * @since 2026-03-18
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
    public Result<PageResult<OrderVO>> listOrders(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String orderNo,
            @RequestParam(required = false) Integer status) {
        Page<OrderVO> pageResult = orderService.listOrders(page, size, orderNo, status);
        PageResult<OrderVO> result = new PageResult<>(
                pageResult.getRecords(),
                pageResult.getTotal(),
                pageResult.getCurrent(),
                pageResult.getSize()
        );
        return Result.success(result);
    }

    @Operation(summary = "取消订单")
    @PutMapping("/{id}/cancel")
    public Result<Boolean> cancelOrder(
            @PathVariable Long id,
            @RequestParam(required = false, defaultValue = "system") String operator) {
        return Result.success(orderService.cancelOrder(id, operator));
    }

    @Operation(summary = "完成订单")
    @PutMapping("/{id}/complete")
    public Result<Boolean> completeOrder(
            @PathVariable Long id,
            @RequestParam(required = false, defaultValue = "system") String operator) {
        return Result.success(orderService.completeOrder(id, operator));
    }

    @Operation(summary = "申请退款")
    @PostMapping("/{id}/refund")
    public Result<Boolean> refundOrder(
            @PathVariable Long id,
            @RequestParam String reason,
            @RequestParam(required = false, defaultValue = "system") String operator) {
        return Result.success(orderService.refundOrder(id, reason, operator));
    }
}
