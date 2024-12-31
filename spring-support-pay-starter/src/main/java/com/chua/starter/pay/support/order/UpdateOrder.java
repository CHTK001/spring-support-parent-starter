package com.chua.starter.pay.support.order;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.chua.starter.pay.support.entity.PayMerchantOrder;
import com.chua.starter.pay.support.mapper.PayMerchantMapper;
import com.chua.starter.pay.support.mapper.PayMerchantOrderMapper;
import com.chua.starter.pay.support.pojo.WechatOrderCallbackRequest;
import com.chua.starter.pay.support.pojo.WechatOrderCallbackResponse;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 更新订单
 * @author CH
 * @since 2024/12/30
 */
public class UpdateOrder {
    private final TransactionTemplate transactionTemplate;
    private final PayMerchantMapper payMerchantMapper;
    private final PayMerchantOrderMapper payMerchantOrderMapper;

    public UpdateOrder(TransactionTemplate transactionTemplate, PayMerchantMapper payMerchantMapper, PayMerchantOrderMapper payMerchantOrderMapper) {
        this.transactionTemplate = transactionTemplate;
        this.payMerchantMapper = payMerchantMapper;
        this.payMerchantOrderMapper = payMerchantOrderMapper;
    }

    /**
     * 支付操作
     * @param wechatOrderCallbackRequest 回调
     * @param tradeType 交易类型
     * @return 回调
     */
    public WechatOrderCallbackResponse update(WechatOrderCallbackRequest wechatOrderCallbackRequest, String tradeType) {
        PayMerchantOrder payMerchantOrder = getPayMerchantOrder(wechatOrderCallbackRequest, tradeType);
        if(null == payMerchantOrder) {
            return new WechatOrderCallbackResponse("FAILURE", "订单不存在", null);
        }

        payMerchantOrder.setPayMerchantOrderStatus("2000");
        payMerchantOrder.setPayMerchantOrderTransactionId(wechatOrderCallbackRequest.getTransactionId());
        try {
            payMerchantOrderMapper.updateById(payMerchantOrder);
        } catch (Exception e) {
            try {
                payMerchantOrder = getPayMerchantOrder(wechatOrderCallbackRequest, tradeType);
                payMerchantOrderMapper.updateById(payMerchantOrder);
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        }
        return new WechatOrderCallbackResponse("SUCCESS", "OK", null);

    }

    /**
     * 支付失败
     * @param wechatOrderCallbackRequest 回调
     * @param tradeType 交易类型
     * @return 回调
     */
    public WechatOrderCallbackResponse failure(WechatOrderCallbackRequest wechatOrderCallbackRequest, String tradeType) {
        PayMerchantOrder payMerchantOrder = getPayMerchantOrder(wechatOrderCallbackRequest, tradeType);

        if(null == payMerchantOrder) {
            return new WechatOrderCallbackResponse("FAILURE", wechatOrderCallbackRequest.getReturnMsg(), null);
        }

        payMerchantOrder.setPayMerchantOrderFailMessage(wechatOrderCallbackRequest.getReturnMsg());
        payMerchantOrder.setPayMerchantOrderStatus("3000");
        try {
            payMerchantOrderMapper.updateById(payMerchantOrder);
        } catch (Exception e) {
            try {
                payMerchantOrder = getPayMerchantOrder(wechatOrderCallbackRequest, tradeType);
                payMerchantOrderMapper.updateById(payMerchantOrder);
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        }
        return new WechatOrderCallbackResponse("FAILURE", wechatOrderCallbackRequest.getReturnMsg(), null);
    }


    /**
     * 获取订单
     * @param wechatOrderCallbackRequest 回调
     * @param tradeType 交易类型
     * @return 订单
     */
    private PayMerchantOrder getPayMerchantOrder(WechatOrderCallbackRequest wechatOrderCallbackRequest, String tradeType) {
        //业务订单
        String outTradeNo = wechatOrderCallbackRequest.getOutTradeNo();
        return payMerchantOrderMapper.selectOne(Wrappers.<PayMerchantOrder>lambdaQuery()
                .eq(PayMerchantOrder::getPayMerchantOrderTradeType, tradeType)
                .eq(PayMerchantOrder::getPayMerchantOrderCode, outTradeNo)
        );
    }
}
