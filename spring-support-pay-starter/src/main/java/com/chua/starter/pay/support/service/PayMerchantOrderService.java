package com.chua.starter.pay.support.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.chua.common.support.lang.code.ReturnPageResult;
import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.mybatis.entity.Query;
import com.chua.starter.pay.support.entity.PayMerchantOrder;
import com.chua.starter.pay.support.entity.PayMerchantOrderWater;
import com.chua.starter.pay.support.pojo.PayMerchantOrderQueryRequest;
import com.chua.starter.pay.support.pojo.PayOrderQueryRequest;

import java.util.List;


/**
 *
 * @since 2024/12/30
 * @author CH
 */
public interface PayMerchantOrderService extends IService<PayMerchantOrder>{



    /**
     * 分页查询
     * @param query 查询条件
     * @param request 查询参数
     * @return 返回结果
     */
    ReturnPageResult<PayMerchantOrder> page(Query<PayMerchantOrder> query, PayOrderQueryRequest request);

    /**
     * 分页查询
     * @param page 分页条件
     * @param request 查询参数
     * @return 返回结果
     */
    ReturnPageResult<PayMerchantOrder> page(Query<PayMerchantOrder> page, PayMerchantOrderQueryRequest request);

    /**
     * 水单
     * @param payMerchantOrderCode 订单编号
     * @return 返回结果
     */
    ReturnResult<List<PayMerchantOrderWater>> water(String payMerchantOrderCode);
}
