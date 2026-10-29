package com.chua.starter.datasourcehub.support.dynamicapi.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * 动态接口保存 / 更新请求。
 *
 * <p>接口本体 = {@code sqlText}（「SQL 查询」页签上面的编辑器内容）；
 * 保存后形成一条 {@code ms_datasource_api} 记录，发布后成为可调用接口。
 * 数据源既可以是普通 JDBC 源，也可以是 {@code type=calcite} 的合并数据源
 * （联合接口，支持跨库 JOIN）。</p>
 *
 * @param apiName         接口名
 * @param apiPath         对外地址段（同一数据源下唯一），如 /order/summary
 * @param apiMethod       HTTP 方法：GET / POST / PUT
 * @param datasourceId    绑定的数据源 id；0 = 应用默认主数据源
 * @param description     描述
 * @param sqlText         保存的 SQL 查询语句（接口本体）
 * @param paramDefs       参数定义列表（可选）
 * @param authMode        鉴权方式：oauth / appkey / none
 * @param cacheTtlSeconds 结果缓存秒数；0 或 null = 不缓存
 * @param publishMode     发布模式：embedded / standalone
 * @param standalonePort  独立 Server 监听端口（standalone 模式；0 = 默认端口）
 * @param enabled         是否启用：0 停用、1 启用
 * @author CH
 * @since 4.0.0.42
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record DynamicApiDTO(
        String apiName,
        String apiPath,
        String apiMethod,
        Long datasourceId,
        String description,
        String sqlText,
        List<ApiParamDef> paramDefs,
        String authMode,
        Integer cacheTtlSeconds,
        String publishMode,
        Integer standalonePort,
        Integer enabled
) {
}