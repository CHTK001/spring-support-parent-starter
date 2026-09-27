package com.chua.payment.support.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 微信支付分创建请求
 * @author CH
 * @since 4.0.0
 */
@Data
public class WechatPayScoreCreateDTO {
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
     * 服务标识
     */
    private String serviceId;
    /**
     * 用户OpenId标识
     */
    private String openId;
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
     * 创建原因
     */
    private String reason;
    /**
     * 完成类型
     */
    private String finishType;
    /**
     * 是否需要用户确认
     */
    private Boolean needUserConfirm;
    /**
     * 附加数据
     */
    private String attach;
    private List<Map<String, Object>> postPayments; // 后付商品列表
    private List<Map<String, Object>> postDiscounts; // 后付优惠列表
    private Map<String, Object> extraParams; // extra参数
    /**
     * 备注
     */
    private String remark;
}
