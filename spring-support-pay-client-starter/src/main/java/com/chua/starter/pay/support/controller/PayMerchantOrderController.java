package com.chua.starter.pay.support.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.chua.common.support.lang.code.ReturnPageResult;
import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.mybatis.entity.Query;
import com.chua.starter.mybatis.utils.ReturnPageResultUtils;
import com.chua.starter.pay.support.entity.PayMerchantFailureRecord;
import com.chua.starter.pay.support.entity.PayMerchantOrder;
import com.chua.starter.pay.support.entity.PayMerchantOrderWater;
import com.chua.starter.pay.support.pojo.PayMerchantOrderPageRequest;
import com.chua.starter.pay.support.pojo.PayMerchantOrderVO;
import com.chua.starter.pay.support.service.PayMerchantFailureRecordService;
import com.chua.starter.pay.support.service.PayMerchantOrderService;
import com.chua.starter.pay.support.service.PayMerchantOrderWaterService;
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
    private final PayMerchantOrderWaterService payMerchantOrderWaterService;
    private final PayMerchantFailureRecordService payMerchantFailureRecordService;

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

    /**
     * 订单流水列表
     */
    @GetMapping("water")
    @Operation(summary = "订单流水列表")
    public ReturnResult<java.util.List<PayMerchantOrderWater>> listWater(String payMerchantOrderCode) {
        java.util.List<PayMerchantOrderWater> list = payMerchantOrderWaterService.list(
                Wrappers.<PayMerchantOrderWater>lambdaQuery()
                        .eq(PayMerchantOrderWater::getPayMerchantOrderCode, payMerchantOrderCode)
                        .orderByAsc(PayMerchantOrderWater::getCreateTime)
        );
        return ReturnResult.ok(list);
    }

    /**
     * 失败原因列表
     */
    @GetMapping("failure")
    @Operation(summary = "失败原因列表")
    public ReturnResult<java.util.List<PayMerchantFailureRecord>> listFailure(String payMerchantOrderCode) {
        java.util.List<PayMerchantFailureRecord> list = payMerchantFailureRecordService.list(
                Wrappers.<PayMerchantFailureRecord>lambdaQuery()
                        .eq(PayMerchantFailureRecord::getPayMerchantMerchantOrderCode, payMerchantOrderCode)
                        .orderByDesc(PayMerchantFailureRecord::getCreateTime)
        );
        return ReturnResult.ok(list);
    }
}
