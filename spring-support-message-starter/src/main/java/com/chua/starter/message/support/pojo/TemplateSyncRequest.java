package com.chua.starter.message.support.pojo;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.Objects;

/**
 * 模板同步请求
 *
 * @param sysMessageProviderId SYS消息提供者标识
 *
 * @since 2026/08/03
 * @author CH
 */
@Schema(description = "模板同步请求")
public record TemplateSyncRequest(
        @Schema(description = "提供商ID") Integer sysMessageProviderId
) {
    /**
     * 规范构造器：提供商标识非空校验。
     *
     * <p>value class 前置条件——语义必填的引用组件必须空值敌对。同步动作以提供商标识
     * 为定位条件，缺失即无同步目标，故拒绝 null。</p>
     *
     * @param sysMessageProviderId SYS消息提供者标识
     */
    public TemplateSyncRequest {
        sysMessageProviderId = Objects.requireNonNull(sysMessageProviderId, "sysMessageProviderId 不能为 null");
    }
}
