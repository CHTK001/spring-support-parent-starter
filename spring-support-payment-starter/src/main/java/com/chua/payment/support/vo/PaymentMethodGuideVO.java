package com.chua.payment.support.vo;

import java.io.Serializable;
import java.util.List;

/**
 * 支付方式开通指南
 *
 * @param channelType       渠道类型
 * @param channelSubType    渠道SUB类型
 * @param title             标题
 * @param officialName      官方名称
 * @param officialUrl       官方链接
 * @param applyUrl          申请链接
 * @param sandboxUrl        沙箱链接
 * @param summary           简介
 * @param requiredMaterials 必填材料
 * @param steps             开通步骤
 * @param tips              小贴士
 * @author CH
 * @since 4.0.0
 */
public record PaymentMethodGuideVO(
        String channelType,
        String channelSubType,
        String title,
        String officialName,
        String officialUrl,
        String applyUrl,
        String sandboxUrl,
        String summary,
        List<String> requiredMaterials,
        List<String> steps,
        List<String> tips) implements Serializable {

    private static final long serialVersionUID = 1L; // 序列化版本uid
}
