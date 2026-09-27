package com.chua.starter.server.support.model.vo;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 计划任务手动执行请求
 * <p>
 * 用于「选择服务器（相同类型）」后按指定主机执行一次任务；
 * 主机集合为空时回落到任务自身配置的目标主机。
 * </p>
 *
 * @param hostIds        本次执行的目标主机 标识 集合（可空）
 * @param timeoutSeconds 单机超时（秒），可空取任务配置
 * @author CH
 * @since 4.0.0.42
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ExecJobRunVO(
        List<Long> hostIds,
        Integer timeoutSeconds
) {

    /**
     * 规范构造器：主机集合做防御性拷贝。
     *
     * <p>value class 前置条件——集合组件必须深不可变。本组件允许为 null（为空时回落到任务自身配置的
     * 目标主机），故保留 null 语义；集合元素来自请求 JSON、可能含 null，故不用 {@code List.copyOf}
     * 而用不可变包装，避免把业务错误变成 NPE。</p>
     *
     * @param hostIds        本次执行的目标主机 标识 集合（可空）
     * @param timeoutSeconds 单机超时（秒），可空取任务配置
     */
    public ExecJobRunVO {
        hostIds = hostIds == null ? null : Collections.unmodifiableList(new ArrayList<>(hostIds));
    }
}
