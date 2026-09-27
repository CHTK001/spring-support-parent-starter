package com.chua.payment.support.vo;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 支付方式开通指南
 * @author CH
 * @since 4.0.0
 */
@Data
public class PaymentMethodGuideVO implements Serializable {
    private static final long serialVersionUID = 1L; // 序列化版本uid

    /**
     * 渠道类型
     */
    private String channelType;

    /**
     * 渠道SUB类型
     */
    private String channelSubType;

    /**
     * 标题
     */
    private String title;

    /**
     * 官方名称
     */
    private String officialName;

    /**
     * 官方链接
     */
    private String officialUrl;

    /**
     * 申请链接
     */
    private String applyUrl;

    /**
     * 沙箱链接
     */
    private String sandboxUrl;

    /**
     * 简介
     */
    private String summary;

    /**
     * 必填材料
     */
    private List<String> requiredMaterials;

    /**
     * 开通步骤
     */
    private List<String> steps;

    /**
     * 小贴士
     */
    private List<String> tips;
}
