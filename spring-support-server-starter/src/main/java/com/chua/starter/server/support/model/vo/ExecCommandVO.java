package com.chua.starter.server.support.model.vo;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

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
}
