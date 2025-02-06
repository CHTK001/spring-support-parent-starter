package com.chua.starter.pay.support.controller;

import com.chua.common.support.lang.code.ReturnPageResult;
import com.chua.common.support.lang.code.ReturnResult;
import com.chua.common.support.validator.group.SelectGroup;
import com.chua.starter.common.support.annotations.Permission;
import com.chua.starter.mybatis.entity.Query;
import com.chua.starter.oauth.client.support.annotation.UserValue;
import com.chua.starter.pay.support.entity.PayMerchantOrder;
import com.chua.starter.pay.support.entity.PayMerchantOrderWater;
import com.chua.starter.pay.support.pojo.PayMerchantOrderQueryRequest;
import com.chua.starter.pay.support.pojo.PayOrderQueryRequest;
import com.chua.starter.pay.support.service.PayMerchantOrderService;
import com.chua.starter.pay.support.service.PayMerchantOrderWaterService;
import io.swagger.annotations.Api;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.validation.BindingResult;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Set;

/**
 * 订单接口
 * @author CH
 * @since 2024/12/30
 */
@Api(tags = "订单接口")
@Tag(name = "订单接口")
@RestController
@RequestMapping("/v3/pay/order/history")
@Slf4j
@RequiredArgsConstructor
public class PayOrderController {


    final PayMerchantOrderService payMerchantOrderService;
    final PayMerchantOrderWaterService payMerchantOrderWaterService;

    /**
     * 分页查询
     *
     * @param page         分页
     * @param request      请求
     * @param bindingResult 验证
     * @return
     */
    @GetMapping("/page")
    @Operation(summary = "分页查询订单(v3)")
    @Permission({"sys:pay:order:page"})
    public ReturnPageResult<PayMerchantOrder> page(@ParameterObject Query<PayMerchantOrder> page,
                                                   @Validated(SelectGroup.class) @ParameterObject PayOrderQueryRequest request,
                                                   @Parameter(hidden = true) @UserValue("roles") Set<String> roles,
                                                   @Parameter(hidden = true) @UserValue("deptId") String deptId,
                                                   @Parameter(hidden = true) @UserValue("userId") String userId,
                                                   BindingResult bindingResult) {
        if(bindingResult.hasErrors()) {
            return ReturnPageResult.illegal(bindingResult.getAllErrors().getFirst().getDefaultMessage());
        }
        request.setPayMerchantDeptId(deptId);
        request.setPayMerchantOrderDeptOrganizer(userId);
        return payMerchantOrderService.page(page, roles, request);
    }
    /**
     * 分页查询
     *
     * @param page         分页
     * @param request      请求
     * @param bindingResult 验证
     * @return
     */
    @GetMapping("/merchant")
    @Operation(summary = "分页查询订单(商家)")
    @Permission({"sys:pay:order:page"})
    public ReturnPageResult<PayMerchantOrder> page(@ParameterObject Query<PayMerchantOrder> page,
                                                   @Validated(SelectGroup.class) @ParameterObject PayMerchantOrderQueryRequest request,
                                                   BindingResult bindingResult) {
        if(bindingResult.hasErrors()) {
            return ReturnPageResult.illegal(bindingResult.getAllErrors().getFirst().getDefaultMessage());
        }
        return payMerchantOrderService.page(page, request);
    }


    /**
     * 订单查询流水
     *
     * @param payMerchantOrderCode 订单编号
     * @return 流水
     */
    @GetMapping("/water")
    @Operation(summary = "订单查询流水")
    @Permission({"sys:pay:order:water:page"})
    public ReturnResult<List<PayMerchantOrderWater>> water(String payMerchantOrderCode) {
        return payMerchantOrderWaterService.water(payMerchantOrderCode);
    }
}
