package com.chua.payment.support.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 微信支付分订单视图
 * @author CH
 * @since 4.0.0
 */
@Data
public class WechatPayScoreOrderVO {
    /**
     * 标识
     */
    private Long id;
    /**
     * 商户ID
     */
    private Long merchantId;
    /**
     * 渠道标识
     */
    private Long channelId;
    /**
     * 用户标识
     */
    private Long userId;
    /**
     * 分订单号
     */
    private String outOrderNo;
    /**
     * 服务订单号
     */
    private String serviceOrderNo;
    /**
     * APPID
     */
    private String appId;
    /**
     * 服务标识
     */
    private String serviceId;
    /**
     * 用户OpenId标识
     */
    private String openId;
    /**
     * 状态
     */
    private String state;
    /**
     * 总金额
     */
    private BigDecimal totalAmount;
    /**
     * 通知URL
     */
    private String notifyUrl;
    /**
     * 服务介绍
     */
    private String serviceIntroduction;
    /**
     * 开始时间
     */
    private String startTime;
    /**
     * 结束时间
     */
    private String endTime;
    /**
     * 完成类型
     */
    private String finishType;
    /**
     * 创建原因
     */
    private String reason;
    /**
     * 完结原因
     */
    private String finishReason;
    /**
     * 包信息
     */
    private String packageInfo;
    /**
     * 附加数据
     */
    private String attach;
    /**
     * 请求报文
     */
    private String requestPayload;
    /**
     * 响应报文
     */
    private String responsePayload;
    /**
     * 备注
     */
    private String remark;
    /**
     * 完成时间
     */
    private LocalDateTime completedAt;
    /**
     * 取消时间
     */
    private LocalDateTime cancelledAt;
    /**
     * 创建时间
     */
    private LocalDateTime createTime;
    /**
     * 更新时间
     */
    private LocalDateTime updateTime;
}
