package com.chua.starter.pay.support.enums;

import lombok.Getter;

/**
 * 批量转账状态
 * @author CH
 * @since 2025/4/16 9:37
 */
@Getter
public enum WechatTransferBatchStatus {

    /**
     * 转账处理中
     */
    PROCESSING("PROCESSING", "转账处理中"),
    /**
     * 转账成功
     */
    SUCCESS("SUCCESS", "转账成功"),
    /**
     * 转账失败
     */
    FAIL("FAIL", "转账失败"),
    /**
     * 部分成功
     */
    PART_SUCCESS("PART_SUCCESS", "部分转账成功"),
    /**
     * 转账超时关闭
     */
    TIMEOUT_CLOSE("TIMEOUT_CLOSE", "转账超时关闭");

    private final String code;
    private final String description;

    WechatTransferBatchStatus(String code, String description) {
        this.code = code;
        this.description = description;
    }

    /**
     * 根据状态码获取枚举
     * @param code 状态码
     * @return 对应的枚举，找不到返回null
     */
    public static WechatTransferBatchStatus getByCode(String code) {
        for (WechatTransferBatchStatus status : values()) {
            if (status.getCode().equals(code)) {
                return status;
            }
        }
        return null;
    }

    public static WechatTransferBatchStatus parse(String state) {
        if("WAIT_USER_CONFIRM".equals(state)) {
            return PROCESSING;
        }
        return getByCode(state);
    }

    /**
     * 判断是否是最终状态(不可再变更的状态)
     */
    public boolean isFinalStatus() {
        return this == SUCCESS || this == FAIL || this == TIMEOUT_CLOSE;
    }

    /**
     * 判断是否处理成功(包括部分成功)
     */
    public boolean isSuccess() {
        return this == SUCCESS || this == PART_SUCCESS;
    }
}
