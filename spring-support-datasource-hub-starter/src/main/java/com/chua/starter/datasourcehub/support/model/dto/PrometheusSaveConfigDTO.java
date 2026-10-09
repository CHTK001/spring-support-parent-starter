package com.chua.starter.datasourcehub.support.model.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Prometheus 配置文件保存请求。
 *
 * @param datasourceId 数据源 ID，可空（退化为第一个 prometheus 数据源）
 * @param path         目标文件路径，空则用配置根下主文件 prometheus.yml
 * @param content      文件内容（整篇覆盖）
 * @param backup       是否先备份原文件，空按 true
 * @param validate     是否用 promtool 校验，空按 true（promtool 不可用时自动跳过）
 * @param reload       写盘后是否触发重载，空按 true
 * @author CH
 * @since 4.0.0.42
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record PrometheusSaveConfigDTO(
        Long datasourceId,
        String path,
        String content,
        Boolean backup,
        Boolean validate,
        Boolean reload
) {
}
