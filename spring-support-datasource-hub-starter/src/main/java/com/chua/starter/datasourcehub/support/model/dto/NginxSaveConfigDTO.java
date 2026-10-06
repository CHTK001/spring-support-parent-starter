package com.chua.starter.datasourcehub.support.model.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * 保存 Nginx 配置请求。
 *
 * @param datasourceId 数据源 ID，可为空
 * @param path        配置文件绝对路径
 * @param content     配置文件完整内容
 * @author CH
 * @since 4.0.0.42
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record NginxSaveConfigDTO(
        Long datasourceId,
        String path,
        String content
) {
}
