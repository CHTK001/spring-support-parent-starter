package com.chua.starter.common.support.api.rule;

import lombok.Getter;

/**
 * 隐私数据类型枚举
 *
 * @author CH
 */
@Getter
public enum PrivacyTypeEnum {
    /**
     * �?
     */
    NONE,

    /**
     * 自定义（此项需设置脱敏的范围）
     */
    CUSTOMER,

    /**
     * 姓名
     */
    NAME,

    /**
     * 身份证号
     */
    ID_CARD,

    /**
     * 手机�?
     */
    PHONE,
    /**
     * 地址
     */
    ADDRESS,
    /**
     * 银行�?
     */
    BANK_CARD,
    /**
     * 密码
     */
    PASSWORD,
    /**
     * 车牌�?
     */
    CAR_NUMBER,
    /**
     * 邮箱
     */
    EMAIL,
}

