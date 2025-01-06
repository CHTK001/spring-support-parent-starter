package com.chua.starter.pay.support.service.impl;


import com.chua.common.support.rpc.RpcService;
import com.chua.starter.pay.support.configuration.PayListenerService;
import com.chua.starter.pay.support.entity.PayMerchantOrder;
import com.chua.starter.pay.support.service.PayCallListener;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RpcService(async = true)
@RequiredArgsConstructor
public class PayCallListenerImpl implements PayCallListener {

    private final PayListenerService factor;
    @Override
    public void listen(PayMerchantOrder order) {
        if(null == factor) {
            return;
        }
        factor.listen(order);
    }
}
