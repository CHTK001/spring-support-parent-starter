package com.chua.starter.eduai.model.vo;

import lombok.Data;

/**
 * 手动触发采集结果VO
 *
 * @since 4.0.0.42
 * @author CH
 */
@Data
public class CollectTriggerVO {

    /**
     * 执行结果（success=成功, fail=失败, skip=跳过）
     */
    private String status;

    /**
     * 结果说明
     */
    private String message;

    /**
     * 构造结果。
     *
     * @param status 状态
     * @param message 说明
     */
    public CollectTriggerVO(String status, String message) {
        this.status = status;
        this.message = message;
    }
}
