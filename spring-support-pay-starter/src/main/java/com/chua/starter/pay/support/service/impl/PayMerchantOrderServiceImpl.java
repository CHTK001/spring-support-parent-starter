package com.chua.starter.pay.support.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.chua.common.support.lang.code.ReturnPageResult;
import com.chua.starter.mybatis.entity.Query;
import com.chua.starter.mybatis.utils.ReturnPageResultUtils;
import com.chua.starter.pay.support.entity.PayMerchantOrder;
import com.chua.starter.pay.support.mapper.PayMerchantOrderMapper;
import com.chua.starter.pay.support.pojo.PayMerchantOrderQueryRequest;
import com.chua.starter.pay.support.service.PayMerchantOrderService;
import org.springframework.stereotype.Service;
/**
 *
 * @since 2024/12/30
 * @author CH    
 */
@Service
public class PayMerchantOrderServiceImpl extends ServiceImpl<PayMerchantOrderMapper, PayMerchantOrder> implements PayMerchantOrderService{


    @Override
    public ReturnPageResult<PayMerchantOrder> page(Query<PayMerchantOrder> query, PayMerchantOrderQueryRequest request) {
        return ReturnPageResultUtils.ok(
                baseMapper.selectPage(query.createPage(), Wrappers.<PayMerchantOrder>lambdaQuery()
                        .eq(PayMerchantOrder::getPayMerchantCode, request.getPayMerchantCode())
                        .ge(null != request.getStartTime(), PayMerchantOrder::getCreateTime, request.getStartTime())
                        .le(null != request.getEndTime(), PayMerchantOrder::getCreateTime, request.getEndTime())
                )
        );
    }
}
