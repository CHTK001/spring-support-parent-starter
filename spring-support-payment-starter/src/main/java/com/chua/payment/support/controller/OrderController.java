package com.chua.payment.support.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.chua.payment.support.channel.PaymentResult;
import com.chua.payment.support.common.PageResult;
import com.chua.payment.support.common.Result;
import com.chua.payment.support.dto.OrderCreateDTO;
import com.chua.payment.support.dto.OrderOperateDTO;
import com.chua.payment.support.dto.OrderPayDTO;
import com.chua.payment.support.dto.PaymentOrderPageRequest;
import com.chua.payment.support.dto.RefundApplyDTO;
import com.chua.payment.support.service.PaymentOrderService;
import com.chua.payment.support.vo.OrderStateLogVO;
import com.chua.payment.support.vo.OrderVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
* 订单管理控制器
* @author CH
* @since 4.0.0
 */
@Tag(name = "订单管理", description = "支付订单相关接口")
@RestController
@RequestMapping("/order")
@RequiredArgsConstructor
public class OrderController {

    /** 排序服务 */
    private final PaymentOrderService orderService;

    @Operation(summary = "创建订单")
    @PostMapping
    /**
    * 创建订单
    *
    * @param dto dto
    * @return 创建订单的结果
    */
    public Result<OrderVO> createOrder(@RequestBody OrderCreateDTO dto) {
        return Result.success(orderService.createOrder(dto));
    }

    @Operation(summary = "查询订单")
    @GetMapping("/{id}")
    /**
    * 获取订单
    *
    * @param id 标识
    * @return 获取订单的结果
    */
    public Result<OrderVO> getOrder(@PathVariable Long id) {
        return Result.success(orderService.getOrder(id));
    }

    @Operation(summary = "分页查询订单")
    @GetMapping("/list")
    /**
    * 列表订单
    *
    * @param "1" "1"
    * @return 列表订单的结果
    */
    public Result<PageResult<OrderVO>> listOrders(@RequestParam(defaultValue = "1") int page,
                                                  @RequestParam(defaultValue = "10") int size,
                                                  @RequestParam(required = false) Long merchantId,
                                                  @RequestParam(required = false) String orderNo,
                                                  @RequestParam(required = false) String status) {
        Page<OrderVO> pageResult = orderService.listOrders(new PaymentOrderPageRequest(page, size, merchantId, orderNo, status));
        return Result.success(PageResult.of(pageResult));
    }

    @Operation(summary = "查询商户订单")
    @GetMapping("/merchant/{merchantId}")
    /**
    * 列表merchant订单
    * @param merchantId merchantid
    * @param page page
    * @param size 大小
    * @param status 状态
    */
    public Result<PageResult<OrderVO>> listMerchantOrders(@PathVariable Long merchantId,
                                                          @RequestParam(defaultValue = "1") int page,
                                                          @RequestParam(defaultValue = "10") int size,
                                                          @RequestParam(required = false) String status) {
        return Result.success(PageResult.of(orderService.listMerchantOrders(merchantId, page, size, status)));
    }

    @Operation(summary = "查询订单状态流转日志")
    @GetMapping("/{id}/logs")
    /**
    * 列表订单日志
    *
    * @param id 标识
    * @return 列表订单日志的结果
    */
    public Result<List<OrderStateLogVO>> listOrderLogs(@PathVariable Long id) {
        return Result.success(orderService.listOrderLogs(id));
    }

    @Operation(summary = "真实发起支付")
    @PostMapping("/{id}/pay")
    /**
    * 薪酬订单
    * @param id 标识
    * @param dto dto
    */
    public Result<PaymentResult> payOrder(@PathVariable Long id,
                                          @RequestBody(required = false) OrderPayDTO dto) {
        return Result.success(orderService.payOrder(id, dto));
    }

    @Operation(summary = "同步第三方支付状态")
    @PostMapping("/{id}/sync")
    /**
    * 同步订单
    *
    * @param id 标识
    * @return 同步订单的结果
    */
    public Result<OrderVO> syncOrder(@PathVariable Long id) {
        return Result.success(orderService.syncOrder(id));
    }

    @Operation(summary = "发起支付")
    @PutMapping("/{id}/pay-start")
    /**
    * 开始薪酬
    * @param id 标识
    * @param dto dto
    */
    public Result<Boolean> startPay(@PathVariable Long id,
                                    @RequestBody(required = false) OrderOperateDTO dto) {
        return Result.success(orderService.startPay(id, dto != null ? dto.getOperator() : null));
    }

    @Operation(summary = "标记支付成功")
    @PutMapping("/{id}/pay-success")
    /**
    * 薪酬成功
    * @param id 标识
    * @param dto dto
    */
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
    /**
    * 薪酬失败
    * @param id 标识
    * @param dto dto
    */
    public Result<Boolean> payFail(@PathVariable Long id,
                                   @RequestBody(required = false) OrderOperateDTO dto) {
        return Result.success(orderService.payFail(id, dto != null ? dto.getRemark() : null, dto != null ? dto.getOperator() : null));
    }

    @Operation(summary = "取消订单", description = "refund=true: 已支付订单退款+取消; refund=false: 不退款仅取消(PENDING/PAYING)")
    @PutMapping("/{id}/cancel")
    /**
    * cancel订单
    * @param id 标识
    * @param refund 退税
    * @param dto dto
    */
    public Result<Boolean> cancelOrder(@PathVariable Long id,
                                       @RequestParam(defaultValue = "false") boolean refund,
                                       @RequestBody(required = false) OrderOperateDTO dto) {
        return Result.success(orderService.cancelOrder(id,
                dto != null ? dto.getOperator() : null,
                dto != null ? dto.getRemark() : null,
                refund));
    }

    @Operation(summary = "完成订单")
    @PutMapping("/{id}/complete")
    /**
    * 完成订单
    * @param id 标识
    * @param dto dto
    */
    public Result<Boolean> completeOrder(@PathVariable Long id,
                                         @RequestBody(required = false) OrderOperateDTO dto) {
        return Result.success(orderService.completeOrder(id, dto != null ? dto.getOperator() : null));
    }

    @Operation(summary = "申请退款")
    @PostMapping("/{id}/refund")
    /**
    * 退税订单
    * @param id 标识
    * @param dto dto
    */
    public Result<Boolean> refundOrder(@PathVariable Long id,
                                       @RequestBody RefundApplyDTO dto) {
        return Result.success(orderService.refundOrder(id, dto));
    }

    @Operation(summary = "标记退款成功")
    @PutMapping("/{id}/refund-success")
    /**
    * 退税成功
    * @param id 标识
    * @param dto dto
    */
    public Result<Boolean> refundSuccess(@PathVariable Long id,
                                         @RequestBody(required = false) OrderOperateDTO dto) {
        return Result.success(orderService.refundSuccess(id, dto != null ? dto.getRefundAmount() : null, dto != null ? dto.getOperator() : null));
    }

    @Operation(summary = "标记退款失败")
    @PutMapping("/{id}/refund-fail")
    /**
    * 退税失败
    * @param id 标识
    * @param dto dto
    */
    public Result<Boolean> refundFail(@PathVariable Long id,
                                      @RequestBody(required = false) OrderOperateDTO dto) {
        return Result.success(orderService.refundFail(id, dto != null ? dto.getRemark() : null, dto != null ? dto.getOperator() : null));
    }

    @Operation(summary = "删除订单")
    @DeleteMapping("/{id}")
    /**
    * 删除订单
    *
    * @param id 标识
    * @return 删除订单的结果
    */
    public Result<Boolean> deleteOrder(@PathVariable Long id) {
        return Result.success(orderService.deleteOrder(id));
    }
}

