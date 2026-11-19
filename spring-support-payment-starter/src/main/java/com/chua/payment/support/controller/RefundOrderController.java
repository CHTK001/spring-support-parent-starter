package com.chua.payment.support.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.chua.common.support.lang.code.ReturnResult;
import com.chua.payment.support.dto.RefundOrderQueryRequest;
import com.chua.payment.support.dto.RefundOperateDTO;
import com.chua.payment.support.dto.RefundSuccessDTO;
import com.chua.payment.support.entity.PaymentRefundOrder;
import com.chua.payment.support.service.PaymentOrderService;
import com.chua.payment.support.service.PaymentRefundOrderService;
import com.chua.payment.support.vo.RefundOrderVO;
import com.chua.starter.common.support.annotations.Permission;
import com.chua.starter.common.support.lang.ReturnPageResult;
import com.chua.starter.mybatis.utils.PageResultUtils;
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

      /**
       * 退款单服务
       */
    private final PaymentRefundOrderService paymentRefundOrderService;
      /**
       * 支付订单服务
       */
    private final PaymentOrderService paymentOrderService;

    @Operation(summary = "查询退款单")
    @GetMapping("/{id}")
    @Permission(value = "payment:refund:view", role = {AuthConstant.ADMIN, AuthConstant.SUPER_ADMIN, AuthConstant.OPS})
      /**
       * 按id获取
       *
       * @param id 标识
       * @return 按id获取的结果
       */
    public ReturnResult<RefundOrderVO> getById(@PathVariable Long id) {
        return ReturnResult.ok(paymentRefundOrderService.getDetail(id));
    }

    @Operation(summary = "查询订单的退款单列表")
    @GetMapping("/order/{orderId}")
    @Permission(value = "payment:refund:view", role = {AuthConstant.ADMIN, AuthConstant.SUPER_ADMIN, AuthConstant.OPS})
      /**
       * 按订单标识查询列表
       *
       * @param orderId 订单标识
       * @return 按订单id查询列表的结果
       */
    public ReturnResult<List<RefundOrderVO>> listByOrderId(@PathVariable Long orderId) {
        return ReturnResult.ok(paymentRefundOrderService.listVoByOrderId(orderId));
    }

    @Operation(summary = "分页查询退款单")
    @GetMapping("/page")
    @Permission(value = "payment:refund:view", role = {AuthConstant.ADMIN, AuthConstant.SUPER_ADMIN, AuthConstant.OPS})
      /**
       * 分页查询退款单。
       *
       * @param pageNum    页码（默认 1）
       * @param pageSize   每页条数（默认 10）
       * @param merchantId 商户标识（可选）
       * @param orderNo    订单号（可选）
       * @param refundNo   退款单号（可选）
       * @param status     退款状态（可选）
       * @return 退款单分页结果
       */
    public ReturnPageResult<RefundOrderVO> page(@RequestParam(defaultValue = "1") int pageNum,
                                                  @RequestParam(defaultValue = "10") int pageSize,
                                                  @RequestParam(required = false) Long merchantId,
                                                  @RequestParam(required = false) String orderNo,
                                                  @RequestParam(required = false) String refundNo,
                                                  @RequestParam(required = false) String status) {
        Page<RefundOrderVO> page = paymentRefundOrderService.page(new RefundOrderQueryRequest(pageNum, pageSize, merchantId, orderNo
            , refundNo, status));
        return PageResultUtils.ok(page);
    }

    @Operation(summary = "标记退款成功")
    @PutMapping("/{id}/success")
    @Permission(value = "payment:refund:success", role = {AuthConstant.ADMIN, AuthConstant.SUPER_ADMIN})
      /**
       * 标记退款成功
       * @param id 标识
       * @param dto dto
       * @return 是否标记成功
       */
    public ReturnResult<Boolean> markRefundSuccess(@PathVariable Long id,
                                             @RequestBody(required = false) RefundOperateDTO dto) {
        PaymentRefundOrder refundOrder = paymentRefundOrderService.getById(id);
        return ReturnResult.ok(paymentOrderService.refundSuccess(new RefundSuccessDTO(
                refundOrder.getRefundNo(),
                dto != null ? dto.refundAmount() : null,
                dto != null ? dto.thirdPartyRefundNo() : null,
                dto != null ? dto.operator() : null,
                null,
                dto != null ? dto.remark() : null)));
    }

    @Operation(summary = "标记退款失败")
    @PutMapping("/{id}/fail")
    @Permission(value = "payment:refund:fail", role = {AuthConstant.ADMIN, AuthConstant.SUPER_ADMIN})
      /**
       * 标记退款失败
       * @param id 标识
       * @param dto dto
       * @return 是否标记成功
       */
    public ReturnResult<Boolean> markRefundFail(@PathVariable Long id,
                                          @RequestBody(required = false) RefundOperateDTO dto) {
        PaymentRefundOrder refundOrder = paymentRefundOrderService.getById(id);
        return ReturnResult.ok(paymentOrderService.refundFail(
                refundOrder.getRefundNo(),
                null,
                dto != null ? dto.operator() : null,
                dto != null ? dto.remark() : null));
    }
}
