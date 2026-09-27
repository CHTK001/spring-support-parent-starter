package com.chua.starter.server.support.model.vo;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 批量命令执行请求
 *
 * @param hostIds        目标主机 标识 集合
 * @param command        待执行命令
 * @param timeoutSeconds 单机超时（秒），为空取服务默认值
 * @author CH
 * @since 4.0.0.42
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ExecCommandVO(
        List<Long> hostIds,
        String command,
        Integer timeoutSeconds
) {

    /**
     * 规范构造器：主机集合做防御性拷贝。
     *
     * <p>value class 前置条件——集合组件必须深不可变。本组件允许为 null（调用方按空集合回落），
     * 故保留 null 语义；集合元素来自请求 JSON、可能含 null，故不用 {@code List.copyOf}
     * 而用不可变包装，避免把业务错误变成 NPE。</p>
     *
     * @param hostIds        目标主机 标识 集合，可为 null
     * @param command        待执行命令
     * @param timeoutSeconds 单机超时（秒），为空取服务默认值
     */
    public ExecCommandVO {
        hostIds = hostIds == null ? null : Collections.unmodifiableList(new ArrayList<>(hostIds));
    }
}
