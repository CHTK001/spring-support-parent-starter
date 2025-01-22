package com.chua.starter.pay.support.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.chua.common.support.bean.BeanUtils;
import com.chua.common.support.lang.code.ReturnPageResult;
import com.chua.common.support.utils.StringUtils;
import com.chua.starter.mybatis.entity.Query;
import com.chua.starter.mybatis.utils.ReturnPageResultUtils;
import com.chua.starter.oauth.client.support.contants.AuthConstant;
import com.chua.starter.pay.support.entity.PayMerchantOrder;
import com.chua.starter.pay.support.mapper.PayMerchantOrderMapper;
import com.chua.starter.pay.support.pojo.PayMerchantOrderQueryRequest;
import com.chua.starter.pay.support.pojo.PayOrderQueryRequest;
import com.chua.starter.pay.support.service.PayMerchantOrderService;
import com.chua.starter.pay.support.service.PayMerchantOrderWaterService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.Set;

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
    public ReturnPageResult<PayMerchantOrder> page(Query<PayMerchantOrder> query, Set<String> roles, PayOrderQueryRequest request) {
        IPage<PayMerchantOrder> payMerchantOrderIPage = findOrder(query, roles, request);
        return ReturnPageResultUtils.ok(payMerchantOrderIPage);
    }

    private IPage<PayMerchantOrder> findOrder(Query<PayMerchantOrder> query, Set<String> roles, PayOrderQueryRequest request) {
        if(AuthConstant.hasAdmin(roles)) {
            return baseMapper.pageForOrder(query.createPage(), request);
        }

        if(AuthConstant.isDept(roles)) {
            if(StringUtils.isEmpty(request.getPayMerchantDeptId())) {
                return new Page<>();
            }
            return baseMapper.pageForOrderDept(query.createPage(), request);
        }

        return new Page<>();
    }

    @Override
    public ReturnPageResult<PayMerchantOrder> page(Query<PayMerchantOrder> page, PayMerchantOrderQueryRequest request) {
        return page(page, Collections.emptySet(), BeanUtils.copyProperties(request, PayOrderQueryRequest.class));
    }


}
