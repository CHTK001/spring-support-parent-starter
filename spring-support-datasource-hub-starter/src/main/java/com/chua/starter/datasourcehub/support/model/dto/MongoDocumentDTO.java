package com.chua.starter.datasourcehub.support.model.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * MongoDB 单文档查看请求
 *
 * @param datasourceId 数据源 ID，非空时按库中配置取文档
 * @param database     逻辑库名，为空用数据源默认库
 * @param collection   集合名
 * @param id           文档 ID（ObjectId 十六进制串，或字符串主键）
 * @author CH
 * @since 4.0.0.42
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record MongoDocumentDTO(
        Long datasourceId,
        String database,
        String collection,
        String id
) {
}
