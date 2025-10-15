package com.chua.starter.pay.support.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.chua.common.support.lang.code.ReturnPageResult;
import com.chua.starter.mybatis.entity.Query;
import com.chua.starter.mybatis.utils.ReturnPageResultUtils;
import com.chua.starter.pay.support.entity.PayMerchantOrder;
import com.chua.starter.pay.support.pojo.PayMerchantOrderPageRequest;
import com.chua.starter.pay.support.pojo.PayMerchantOrderVO;
import com.chua.starter.pay.support.service.PayMerchantOrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 订单管理接口
 * 作者: CH
 * 创建时间: 2025-10-15 16:25
 * 版本: 1.0.0
 */
@Slf4j
@RestController
@RequestMapping("/v2/pay/order")
@Tag(name = "订单管理接口")
@RequiredArgsConstructor
public class PayMerchantOrderController {

    private final PayMerchantOrderService payMerchantOrderService;

    /**
     * 订单分页查询（支持状态、商户、支付时间、完成时间过滤；已关联商户名）
     * @param page 分页参数
     * @param entity 基础查询参数
     * @param cond 额外过滤条件
     * @return 分页数据
     */
    @GetMapping("page")
    @Operation(summary = "订单分页查询")
    public ReturnPageResult<PayMerchantOrderVO> pageForOrder(Query<PayMerchantOrder> page, PayMerchantOrder entity, PayMerchantOrderPageRequest cond) {
        IPage<PayMerchantOrderVO> rs = payMerchantOrderService.pageForPayMerchantOrder(page, entity, cond);
        return ReturnPageResultUtils.ok(rs);
    }
}