package com.chua.starter.monitor.support.model.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * 中间件连通性测试请求
 *
 * <p>登记过的数据源直接传 {@code datasourceId}；未登记时可临时给出 host/port 做即席探测，
 * 后者优先级低于 datasourceId。</p>
 *
 * @param datasourceId 数据源 ID，非空时按库中配置测试
 * @param host         主机地址（即席测试用）
 * @param port         端口（即席测试用）
 * @param contextPath  上下文路径，为空用该中间件默认值
 * @param username     用户名，为空表示不鉴权
 * @param password     密码
 * @author CH
 * @since 4.0.0.42
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record MiddlewareTestDTO(
        Long datasourceId,
        String host,
        Integer port,
        String contextPath,
        String username,
        String password
) {
}
