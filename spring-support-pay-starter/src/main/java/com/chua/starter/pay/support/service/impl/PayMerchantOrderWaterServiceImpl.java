package com.chua.starter.pay.support.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.chua.common.support.lang.code.ReturnResult;
import com.chua.common.support.lang.date.DateUtils;
import com.chua.common.support.lang.date.constant.DateFormatConstant;
import com.chua.common.support.utils.RandomUtils;
import com.chua.common.support.utils.StringUtils;
import com.chua.common.support.utils.ThreadUtils;
import com.chua.starter.pay.support.entity.PayMerchantOrder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.chua.starter.pay.support.entity.PayMerchantOrderWater;
import com.chua.starter.pay.support.mapper.PayMerchantOrderWaterMapper;
import com.chua.starter.pay.support.service.PayMerchantOrderWaterService;
@Service
public class PayMerchantOrderWaterServiceImpl extends ServiceImpl<PayMerchantOrderWaterMapper, PayMerchantOrderWater> implements PayMerchantOrderWaterService{



    private static final ExecutorService POOLS
            = ThreadUtils.newVirtualThreadExecutor();

    @Override
    public ReturnResult<Boolean> createOrderWater(PayMerchantOrder payMerchantOrder) {
        PayMerchantOrderWater payMerchantOrderWater = new PayMerchantOrderWater();
        payMerchantOrderWater.setCreateTime(LocalDateTime.now());
        String format = DateUtils.format(payMerchantOrderWater.getCreateTime(), DateFormatConstant.YYYYMMDDHHMMSS);
        payMerchantOrderWater.setPayMerchantOrderWaterCode("S" + RandomUtils.randomString(6).toUpperCase()
                + "P" + payMerchantOrderWater.getPayMerchantOrderCode()
                + "T" + format
        );
        payMerchantOrderWater.setPayMerchantOrderCode(payMerchantOrder.getPayMerchantOrderCode());
        payMerchantOrderWater.setPayMerchantOrderFailMessage(payMerchantOrder.getPayMerchantOrderFailMessage());
        payMerchantOrderWater.setPayMerchantOrderRefundSuccessTime(payMerchantOrder.getPayMerchantOrderRefundSuccessTime());
        payMerchantOrderWater.setPayMerchantOrderStatus(payMerchantOrder.getPayMerchantOrderStatus());
        payMerchantOrderWater.setPayMerchantOrderWallet(payMerchantOrder.getPayMerchantOrderWallet());
        payMerchantOrderWater.setPayMerchantOrderRefundTransactionId(payMerchantOrder.getPayMerchantOrderRefundTransactionId());
        payMerchantOrderWater.setPayMerchantOrderRefundReason(payMerchantOrder.getPayMerchantOrderRefundReason());

        POOLS.execute(() -> {
            try {
                baseMapper.insert(payMerchantOrderWater);
            } catch (Exception e) {
                e.printStackTrace();
            }
        });

        return ReturnResult.ok();
    }

    @Override
    public ReturnResult<List<PayMerchantOrderWater>> water(String payMerchantOrderCode) {
        if(StringUtils.isEmpty(payMerchantOrderCode)) {
            return ReturnResult.ok(Collections.emptyList());
        }
        return ReturnResult.ok(baseMapper.selectList(
                Wrappers.<PayMerchantOrderWater>lambdaQuery()
                        .eq(PayMerchantOrderWater::getPayMerchantOrderCode, payMerchantOrderCode)
                        .orderByDesc(PayMerchantOrderWater::getCreateTime)
        ));
    }
}
