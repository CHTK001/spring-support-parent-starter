package com.chua.payment.support.vo;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 支付方式开通指引
 */
@Data
public class PaymentMethodGuideVO implements Serializable {

    private String channelType;

    private String channelSubType;

    private String title;

    private String officialName;

    private String officialUrl;

    private String applyUrl;

    private String sandboxUrl;

    private String summary;

    private List<String> requiredMaterials;

    private List<String> steps;

    private List<String> tips;
}
