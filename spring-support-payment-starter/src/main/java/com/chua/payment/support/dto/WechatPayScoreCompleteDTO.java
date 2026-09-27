package com.chua.payment.support.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 微信支付分完结请求
 * @author CH
 * @since 4.0.0
 */
@Data
public class WechatPayScoreCompleteDTO {
    /**
     * 总金额
     */
    private BigDecimal totalAmount;
    /**
     * 完成类型
     */
    private String finishType;
    /**
     * 完结原因
     */
    private String reason;
    /**
     * 结束时间
     */
    private String endTime;
    private List<Map<String, Object>> postPayments; // 后付商品列表
    private List<Map<String, Object>> postDiscounts; // 后付优惠列表
    private Map<String, Object> extraParams; // extra参数
    /**
     * 备注
     */
    private String remark;
}
