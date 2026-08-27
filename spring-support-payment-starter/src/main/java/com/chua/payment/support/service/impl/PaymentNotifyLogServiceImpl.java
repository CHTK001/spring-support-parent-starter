package com.chua.payment.support.service.impl;

import com.chua.payment.support.dto.FinishNotifyLogDTO;
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
 * 支付回调日志服务实现，记录各渠道异步通知的请求与响应详情。
 *
 * @author CH
 * @since 4.0.0.50
 */
@Service
@RequiredArgsConstructor
public class PaymentNotifyLogServiceImpl implements PaymentNotifyLogService {

    /** Paymentnotify日志mapper */
    private final PaymentNotifyLogMapper paymentNotifyLogMapper;
    /** Merchant通道mapper */
    private final MerchantChannelMapper merchantChannelMapper;

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    /**
     * 开始
     * @param notifyType notifyType
     * @param channelId channelId
     * @param requestHeaders requestHeaders
     * @param requestBody requestBody
     */
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
    /**
     * Finish
     * @param logId logId
     * @param signVerified signVerified
     * @param channelType channelType
     * @param channelSubType channelSubType
     * @param processStatus processStatus
     * @param processResult processResult
     * @param relatedOrderId relatedOrderId
     * @param relatedRefundId relatedRefundId
     */
    public void finish(FinishNotifyLogDTO dto) {
        if (dto.logId() == null) {
            return;
        }
        PaymentNotifyLog entity = paymentNotifyLogMapper.selectById(dto.logId());
        if (entity == null) {
            return;
        }
        entity.setSignVerified(dto.signVerified() != null ? dto.signVerified() : entity.getSignVerified());
        if (dto.channelType() != null) {
            entity.setChannelType(dto.channelType());
        }
        if (dto.channelSubType() != null) {
            entity.setChannelSubType(dto.channelSubType());
        }
        entity.setProcessStatus(dto.processStatus());
        entity.setProcessResult(dto.processResult());
        entity.setRelatedOrderId(dto.relatedOrderId());
        entity.setRelatedRefundId(dto.relatedRefundId());
        entity.setProcessedTime(LocalDateTime.now());
        paymentNotifyLogMapper.updateById(entity);
    }
}

