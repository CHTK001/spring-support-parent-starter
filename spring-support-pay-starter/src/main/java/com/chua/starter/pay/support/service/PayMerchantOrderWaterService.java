package com.chua.starter.pay.support.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.chua.common.support.lang.code.ReturnPageResult;
import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.pay.support.entity.PayMerchantOrder;
import com.chua.starter.pay.support.entity.PayMerchantOrderWater;
import com.baomidou.mybatisplus.extension.service.IService;
import com.chua.starter.pay.support.pojo.WaterQueryV1Request;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

public interface PayMerchantOrderWaterService extends IService<PayMerchantOrderWater> {


    /**
     * 创建订单流水
     *
     * @param payMerchantOrder 订单
     * @return 是否成功
     */
    ReturnResult<Boolean> createOrderWater(PayMerchantOrder payMerchantOrder);

    /**
     * 订单流水
     *
     * @param payMerchantOrderCode 订单号
     * @return 订单流水
     */
    ReturnResult<List<PayMerchantOrderWater>> water(String payMerchantOrderCode);

    /**
     * 订单流水
     *
     * @param page      分页数据
     * @param request   请求
     * @param userIds   用户id
     * @param startDate 开始时间
     * @param endDate   结束时间
     * @return 订单流水
     */
    ReturnPageResult<PayMerchantOrderWater> water(Page<PayMerchantOrderWater> page,
                                                  WaterQueryV1Request request,
                                                  Set<String> userIds,
                                                  LocalDate startDate,
                                                  LocalDate endDate);

    /**
     * 创建新表
     *
     * @return 是否成功
     */
    ReturnResult<Boolean> createNewTable();
}
