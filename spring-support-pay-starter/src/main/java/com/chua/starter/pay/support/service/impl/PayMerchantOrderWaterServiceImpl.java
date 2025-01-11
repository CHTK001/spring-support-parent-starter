package com.chua.starter.pay.support.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.chua.common.support.lang.code.ReturnPageResult;
import com.chua.common.support.lang.code.ReturnResult;
import com.chua.common.support.lang.date.DateUtils;
import com.chua.common.support.lang.date.constant.DateFormatConstant;
import com.chua.common.support.spi.ServiceProvider;
import com.chua.common.support.utils.RandomUtils;
import com.chua.common.support.utils.StringUtils;
import com.chua.common.support.utils.ThreadUtils;
import com.chua.starter.mybatis.utils.ReturnPageResultUtils;
import com.chua.starter.pay.support.entity.PayMerchantOrder;
import com.chua.starter.pay.support.pojo.WaterQueryV1Request;
import com.chua.starter.pay.support.transfer.WalletCurrentTransfer;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Set;
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
                + "P" + payMerchantOrder.getPayMerchantOrderCode()
                + "T" + format
        );
        WalletCurrentTransfer currentTransfer = ServiceProvider.of(WalletCurrentTransfer.class).getNewExtension("WALLET");

        payMerchantOrderWater.setPayMerchantOrderCode(payMerchantOrder.getPayMerchantOrderCode());
        payMerchantOrderWater.setPayMerchantOrderFailMessage(payMerchantOrder.getPayMerchantOrderFailMessage());
        payMerchantOrderWater.setPayMerchantOrderRefundSuccessTime(payMerchantOrder.getPayMerchantOrderRefundSuccessTime());
        payMerchantOrderWater.setPayMerchantOrderStatus(payMerchantOrder.getPayMerchantOrderStatus());
        payMerchantOrderWater.setPayMerchantOrderWallet(currentTransfer.getCurrentWallet(payMerchantOrder.getPayMerchantOrderUserId()));
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

    @Override
    public ReturnPageResult<PayMerchantOrderWater> water(Page<PayMerchantOrderWater> page, WaterQueryV1Request request, Set<String> userIds, LocalDate startDate, LocalDate endDate) {
        if(userIds.isEmpty()) {
            return ReturnPageResult.ok(Collections.emptyList());
        }

        if(startDate == null && endDate == null) {
            return ReturnPageResult.illegal("请选择查询时间");
        }

        if(startDate == null) {
            startDate = endDate.minusMonths(1);
        }

        if(endDate == null) {
            endDate = startDate.plusMonths(1);
        }

        if(startDate.isAfter(endDate)) {
            return ReturnPageResult.illegal("请选择正确的时间");
        }

        if(java.time.Period.between(startDate, endDate).getDays() > 30) {
            return ReturnPageResult.illegal("时间间隔不能超过30天");
        }

        return ReturnPageResultUtils.ok(baseMapper.water(page, request, userIds, startDate, endDate));
    }

    @Override
    public ReturnResult<Boolean> createNewTable() {
        return null;
    }
}
