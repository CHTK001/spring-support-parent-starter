package com.chua.starter.datasourcehub.support.model.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * 新建 Calcite 合并数据源请求。
 *
 * @param name      合并数据源名称
 * @param memberIds 参与联合查询的成员数据源 ID 列表
 * @author CH
 * @since 4.0.0.43
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record DataSourceMergeDTO(
        String name,
        List<Long> memberIds
) {
}