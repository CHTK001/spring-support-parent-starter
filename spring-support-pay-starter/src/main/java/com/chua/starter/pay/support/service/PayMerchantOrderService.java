package com.chua.starter.pay.support.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.chua.common.support.lang.code.ReturnPageResult;
import com.chua.starter.mybatis.entity.Query;
import com.chua.starter.pay.support.entity.PayMerchantOrder;
import com.chua.starter.pay.support.pojo.PayMerchantOrderQueryRequest;
import com.chua.starter.pay.support.pojo.PayOrderQueryRequest;

import java.util.Set;


/**
 * @author CH
 * @since 2024/12/30
 */
public interface PayMerchantOrderService extends IService<PayMerchantOrder> {


    /**
     * 分页查询
     *
     * @param query   查询条件
     * @param roles   角色
     * @param request 查询参数
     * @return 返回结果
     */
    ReturnPageResult<PayMerchantOrder> page(Query<PayMerchantOrder> query, Set<String> roles, PayOrderQueryRequest request);

    /**
     * 分页查询
     *
     * @param page    分页条件
     * @param request 查询参数
     * @return 返回结果
     */
    ReturnPageResult<PayMerchantOrder> page(Query<PayMerchantOrder> page, PayMerchantOrderQueryRequest request);

}
