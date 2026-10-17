package com.chua.payment.support.vo;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 微信支付分订单视图
 *
 * @param id                  标识
 * @param merchantId          商户ID
 * @param channelId           渠道标识
 * @param userId              用户标识
 * @param outOrderNo          分订单号
 * @param serviceOrderNo      服务订单号
 * @param appId               APPID
 * @param serviceId           服务标识
 * @param openId              用户OpenId标识
 * @param state               状态
 * @param totalAmount         总金额
 * @param notifyUrl           通知URL
 * @param serviceIntroduction 服务介绍
 * @param startTime           开始时间
 * @param endTime             结束时间
 * @param finishType          完成类型
 * @param reason              创建原因
 * @param finishReason        完结原因
 * @param packageInfo         包信息
 * @param attach              附加数据
 * @param requestPayload      请求报文
 * @param responsePayload     响应报文
 * @param remark              备注
 * @param completedAt         完成时间
 * @param cancelledAt         取消时间
 * @param createTime          创建时间
 * @param updateTime          更新时间
 * @author CH
 * @since 4.0.0
 */
public record WechatPayScoreOrderVO(
        Long id,
        Long merchantId,
        Long channelId,
        Long userId,
        String outOrderNo,
        String serviceOrderNo,
        String appId,
        String serviceId,
        String openId,
        String state,
        BigDecimal totalAmount,
        String notifyUrl,
        String serviceIntroduction,
        String startTime,
        String endTime,
        String finishType,
        String reason,
        String finishReason,
        String packageInfo,
        String attach,
        String requestPayload,
        String responsePayload,
        String remark,
        LocalDateTime completedAt,
        LocalDateTime cancelledAt,
        LocalDateTime createTime,
        LocalDateTime updateTime) {
}
