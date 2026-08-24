package com.chua.payment.support.vo;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 支付方式开通指引
 * @author CH
 */
@Data
public class PaymentMethodGuideVO implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 通道类型 */
    private String channelType;

    /** 通道SUB类型 */
    private String channelSubType;

    /** 标题 */
    private String title;

    /** Official名称 */
    private String officialName;

    /** OfficialURL */
    private String officialUrl;

    /** ApplyURL */
    private String applyUrl;

    /** SandboxURL */
    private String sandboxUrl;

    /** Summary */
    private String summary;

    /** 是否必填materials */
    private List<String> requiredMaterials;

    /** Steps */
    private List<String> steps;

    /** Tips */
    private List<String> tips;
}
