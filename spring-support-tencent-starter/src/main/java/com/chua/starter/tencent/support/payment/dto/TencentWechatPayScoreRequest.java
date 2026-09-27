package com.chua.starter.tencent.support.payment.dto;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 微信支付分服务订单请求
 *
 * @param appId APPID
 * @param serviceId 服务标识
 * @param outOrderNo 出排序NO
 * @param openId 打开标识
 * @param notifyUrl 通知url
 * @param serviceIntroduction 服务introduction
 * @param startTime 开始时间
 * @param endTime 结束时间
 * @param totalAmountFen 总数amountfen
 * @param reason ReasonMLmlml
 * @param finishType 饰面类型
 * @param needUserConfirm Need用户confirm
 * @param attach Attach
 * @param postPayments postPayments
 * @param postDiscounts postDiscounts
 * @param extraParams extraParams
 *
 * @author CH
 * @since 4.0.0
 */
public record TencentWechatPayScoreRequest(
        String appId,
        String serviceId,
        String outOrderNo,
        String openId,
        String notifyUrl,
        String serviceIntroduction,
        String startTime,
        String endTime,
        Integer totalAmountFen,
        String reason,
        String finishType,
        Boolean needUserConfirm,
        String attach,
        List<Map<String, Object>> postPayments,
        List<Map<String, Object>> postDiscounts,
        Map<String, Object> extraParams
) {

    /**
     * 规范构造器：后付费、后折扣与透传参数做防御性拷贝。
     *
     * <p>value class 前置条件——集合组件必须深不可变。本记录是开放给外部调用方的请求载体，
     * 三个集合均可缺省，且元素来自外部 JSON，允许存在空值，故用可空安全包装而非
     * {@code List.copyOf} / {@code Map.copyOf}。</p>
     *
     * @param postPayments  后付费项
     * @param postDiscounts 后折扣项
     * @param extraParams   透传参数
     */
    public TencentWechatPayScoreRequest {
        postPayments = postPayments == null ? null : Collections.unmodifiableList(new ArrayList<>(postPayments));
        postDiscounts = postDiscounts == null ? null : Collections.unmodifiableList(new ArrayList<>(postDiscounts));
        extraParams = extraParams == null ? null : Collections.unmodifiableMap(new LinkedHashMap<>(extraParams));
    }
}

