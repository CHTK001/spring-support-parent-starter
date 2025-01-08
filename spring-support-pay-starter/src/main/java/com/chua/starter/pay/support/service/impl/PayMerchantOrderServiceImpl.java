package com.chua.starter.pay.support.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.chua.common.support.bean.BeanUtils;
import com.chua.common.support.lang.code.ReturnPageResult;
import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.mybatis.entity.Query;
import com.chua.starter.mybatis.utils.ReturnPageResultUtils;
import com.chua.starter.pay.support.entity.PayMerchantOrder;
import com.chua.starter.pay.support.entity.PayMerchantOrderWater;
import com.chua.starter.pay.support.mapper.PayMerchantOrderMapper;
import com.chua.starter.pay.support.pojo.PayMerchantOrderQueryRequest;
import com.chua.starter.pay.support.pojo.PayOrderQueryRequest;
import com.chua.starter.pay.support.service.PayMerchantOrderService;
import com.chua.starter.pay.support.service.PayMerchantOrderWaterService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 *
 * @since 2024/12/30
 * @author CH    
 */
@Service
@RequiredArgsConstructor
public class PayMerchantOrderServiceImpl extends ServiceImpl<PayMerchantOrderMapper, PayMerchantOrder> implements PayMerchantOrderService{

    private final PayMerchantOrderWaterService payMerchantOrderWaterService;

    @Override
    public ReturnPageResult<PayMerchantOrder> page(Query<PayMerchantOrder> query, PayOrderQueryRequest request) {
        return ReturnPageResultUtils.ok(
                baseMapper.pageForOrder(query.createPage(), request)
        );
    }

    @Override
    public ReturnPageResult<PayMerchantOrder> page(Query<PayMerchantOrder> page, PayMerchantOrderQueryRequest request) {
        return page(page, BeanUtils.copyProperties(request, PayOrderQueryRequest.class));
    }


}
