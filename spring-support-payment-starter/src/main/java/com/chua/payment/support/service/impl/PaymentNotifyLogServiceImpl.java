package com.chua.payment.support.service.impl;

import com.chua.payment.support.entity.PaymentNotifyLog;
import com.chua.payment.support.mapper.PaymentNotifyLogMapper;
import com.chua.payment.support.mapper.MerchantChannelMapper;
import com.chua.payment.support.entity.MerchantChannel;
import com.chua.payment.support.service.PaymentNotifyLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 支付回调日志服务实现
 */
@Service
@RequiredArgsConstructor
public class PaymentNotifyLogServiceImpl implements PaymentNotifyLogService {

    private final PaymentNotifyLogMapper paymentNotifyLogMapper;
    private final MerchantChannelMapper merchantChannelMapper;

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public PaymentNotifyLog start(String notifyType,
                                  Long channelId,
                                  String requestHeaders,
                                  String requestBody) {
        PaymentNotifyLog entity = new PaymentNotifyLog();
        entity.setNotifyType(notifyType);
        entity.setChannelId(channelId);
        MerchantChannel channel = channelId == null ? null : merchantChannelMapper.selectById(channelId);
        if (channel != null) {
            entity.setChannelType(channel.getChannelType());
            entity.setChannelSubType(channel.getChannelSubType());
        }
        entity.setRequestHeaders(requestHeaders);
        entity.setRequestBody(requestBody);
        entity.setSignVerified(0);
        entity.setProcessStatus("PROCESSING");
        entity.setRetryCount(0);
        entity.setReceivedTime(LocalDateTime.now());
        paymentNotifyLogMapper.insert(entity);
        return entity;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void finish(Long logId,
                       Integer signVerified,
                       String channelType,
                       String channelSubType,
                       String processStatus,
                       String processResult,
                       Long relatedOrderId,
                       Long relatedRefundId) {
        if (logId == null) {
            return;
        }
        PaymentNotifyLog entity = paymentNotifyLogMapper.selectById(logId);
        if (entity == null) {
            return;
        }
        entity.setSignVerified(signVerified != null ? signVerified : entity.getSignVerified());
        if (channelType != null) {
            entity.setChannelType(channelType);
        }
        if (channelSubType != null) {
            entity.setChannelSubType(channelSubType);
        }
        entity.setProcessStatus(processStatus);
        entity.setProcessResult(processResult);
        entity.setRelatedOrderId(relatedOrderId);
        entity.setRelatedRefundId(relatedRefundId);
        entity.setProcessedTime(LocalDateTime.now());
        paymentNotifyLogMapper.updateById(entity);
    }
}
