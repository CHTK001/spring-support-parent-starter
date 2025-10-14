package com.chua.starter.pay.support.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.chua.common.support.lang.code.ReturnResult;
import com.chua.common.support.spi.ServiceProvider;
import com.chua.common.support.utils.IdUtils;
import com.chua.starter.common.support.utils.RequestUtils;
import com.chua.starter.oauth.client.support.user.UserResume;
import com.chua.starter.pay.support.entity.PayMerchantOrderWater;
import com.chua.starter.pay.support.enums.PayOrderStatus;
import com.chua.starter.pay.support.order.CreateOrderAdaptor;
import com.chua.starter.pay.support.order.CreateSignAdaptor;
import com.chua.starter.pay.support.pojo.CreateOrderV2Request;
import com.chua.starter.pay.support.pojo.CreateOrderV2Response;
import com.chua.starter.pay.support.pojo.PaySignResponse;
import com.chua.starter.pay.support.postprocessor.PayCreateOrderPostprocessor;
import com.chua.starter.pay.support.preprocess.PayCreateOrderPreprocess;
import com.chua.starter.pay.support.service.PayMerchantOrderWaterService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.chua.starter.pay.support.mapper.PayMerchantOrderMapper;
import com.chua.starter.pay.support.entity.PayMerchantOrder;
import com.chua.starter.pay.support.service.PayMerchantOrderService;
import org.springframework.transaction.support.TransactionTemplate;

/**
 *
 * @author CH
 * @since 2025/10/14 11:28
 */
@Service
@RequiredArgsConstructor
public class PayMerchantOrderServiceImpl extends ServiceImpl<PayMerchantOrderMapper, PayMerchantOrder> implements PayMerchantOrderService{

    final TransactionTemplate transactionTemplate;
    final PayMerchantOrderWaterService payMerchantOrderWaterService;

    @Override
    public ReturnResult<CreateOrderV2Response> createOrder(CreateOrderV2Request request) {
        String userId = RequestUtils.getUserId();
        String openId = RequestUtils.getUserInfo(UserResume.class).getOpenId();
        if(!request.hasTradeType()) {
            return ReturnResult.illegal("请选择交易类型");
        }

        if(!request.hasAmount()) {
            return ReturnResult.illegal("请选择金额");
        }

        if(null == request.getPayMerchantId()) {
            return ReturnResult.illegal("请选择商户");
        }
        //创建订单预处理 -> 支持SPI优先级覆盖
        PayCreateOrderPreprocess createOrderPreprocess = PayCreateOrderPreprocess.createProcessor();
        ReturnResult<String> preprocess = createOrderPreprocess.preprocess(request, userId, openId);
        if(preprocess.isFailure()) {
            return ReturnResult.illegal(preprocess.getMsg());
        }

        CreateOrderAdaptor createOrderAdaptor = ServiceProvider.of(CreateOrderAdaptor.class).getNewExtension(request.getPayTradeType());
        if(null == createOrderAdaptor) {
            return ReturnResult.illegal("请选择正确的交易类型");
        }
        ReturnResult<CreateOrderV2Response> order = createOrderAdaptor.createOrder(request, userId, openId);
        if(order.isOk()) {
            //后缀 处理
            PayCreateOrderPostprocessor postprocessor = PayCreateOrderPostprocessor.createProcessor();
            postprocessor.publish(order.getData());
        }
        return order;
    }

    @Override
    public boolean saveOrder(PayMerchantOrder payMerchantOrder) {
        return Boolean.TRUE.equals(transactionTemplate.execute(status -> {
            this.save(payMerchantOrder);
            PayMerchantOrderWater payMerchantOrderWater = new PayMerchantOrderWater();
            payMerchantOrderWater.setPayMerchantOrderCode(payMerchantOrder.getPayMerchantOrderCode());
            payMerchantOrderWater.setPayMerchantOrderStatus(payMerchantOrder.getPayMerchantOrderStatus());
            payMerchantOrderWater.setPayMerchantOrderWaterCode("W" + IdUtils.createTimeId(31));
            return payMerchantOrderWaterService.save(payMerchantOrderWater);
        }));
    }

    @Override
    public ReturnResult<PaySignResponse> createSign(CreateOrderV2Response request) {
        PayMerchantOrder merchantOrder = this.getByCode(request.getPayMerchantOrderCode());
        if(null == merchantOrder) {
            return ReturnResult.illegal("订单不存在");
        }

        CreateSignAdaptor createSignAdaptor = ServiceProvider.of(CreateSignAdaptor.class).getNewExtension(merchantOrder.getPayMerchantTradeType());
        if(null == createSignAdaptor) {
            return ReturnResult.illegal("当前订单不支持签名");
        }
        return createSignAdaptor.createSign(merchantOrder, request.getPrepayId());
    }

    @Override
    public PayMerchantOrder getByCode(String payMerchantOrderCode) {
        return this.getOne(Wrappers.<PayMerchantOrder>lambdaQuery().eq(PayMerchantOrder::getPayMerchantOrderCode, payMerchantOrderCode), false);
    }
}
