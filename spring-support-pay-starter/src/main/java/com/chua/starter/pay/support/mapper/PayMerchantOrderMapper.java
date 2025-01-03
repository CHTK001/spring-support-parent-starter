package com.chua.starter.pay.support.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.chua.starter.pay.support.entity.PayMerchantOrder;
import com.chua.starter.pay.support.pojo.PayMerchantOrderQueryRequest;
import org.apache.ibatis.annotations.Param;

/**
 * @author CH
 * @since 2024/12/30
 */
public interface PayMerchantOrderMapper extends BaseMapper<PayMerchantOrder> {
    /**
     * 分页查询
     *
     * @param page       分页
     * @param request    请求
     * @return
     */
    IPage<PayMerchantOrder> pageForOrder(@Param("page") Page<PayMerchantOrder> page,@Param("request") PayMerchantOrderQueryRequest request);
}