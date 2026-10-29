package com.chua.starter.datasourcehub.support.dynamicapi.model;

import com.chua.common.support.lang.json.Json;
import com.chua.starter.datasourcehub.support.entity.MsDatasourceApi;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 动态接口视图（出参）。
 *
 * <p>管理界面看到的接口定义；{@code datasourceName} 由服务端把台账名
 * 拼进来，前端「左树右盘」按数据源分组时免了一次联查。
 * {@code publishState} 反应当前实例上的发布状态（未发布 / 已发布），
 * 与库里的 {@code publishMode}（期望模式）是两回事 —— 期望是 embedded，
 * 但当前进程没注册过，就是未发布。</p>
 *
 * @param id              主键
 * @param apiName         接口名
 * @param apiPath         对外地址段
 * @param apiMethod       HTTP 方法
 * @param datasourceId    绑定的数据源 id
 * @param datasourceName  绑定数据源名称（服务端联查补齐）
 * @param datasourceType  绑定数据源类型；calcite = 联合接口
 * @param description     描述
 * @param sqlText         SQL 查询语句
 * @param paramDefs       参数定义列表
 * @param authMode        鉴权方式
 * @param cacheTtlSeconds 缓存秒数
 * @param publishMode     发布模式
 * @param standalonePort  独立 Server 端口
 * @param enabled         是否启用
 * @param publishState    当前实例上的发布状态：PUBLISHED / UNPUBLISHED
 * @param createTime      创建时间
 * @param updateTime      更新时间
 * @author CH
 * @since 4.0.0.42
 */
public record DynamicApiVO(
        Long id,
        String apiName,
        String apiPath,
        String apiMethod,
        Long datasourceId,
        String datasourceName,
        String datasourceType,
        String description,
        String sqlText,
        List<ApiParamDef> paramDefs,
        String authMode,
        Integer cacheTtlSeconds,
        String publishMode,
        Integer standalonePort,
        Integer enabled,
        String publishState,
        LocalDateTime createTime,
        LocalDateTime updateTime
) {

    /**
     * 实体转视图（不含数据源名/类型与发布状态，由服务端后续补齐）。
     *
     * @param entity 实体
     * @return 视图
     */
    public static DynamicApiVO of(MsDatasourceApi entity) {
        return new DynamicApiVO(
                entity.getId(),
                entity.getApiName(),
                entity.getApiPath(),
                entity.getApiMethod(),
                entity.getDatasourceId(),
                null,
                null,
                entity.getDescription(),
                entity.getSqlText(),
                parseParams(entity.getParamDefs()),
                entity.getAuthMode(),
                entity.getCacheTtlSeconds(),
                entity.getPublishMode(),
                entity.getStandalonePort(),
                entity.getEnabled(),
                null,
                entity.getCreateTime(),
                entity.getUpdateTime());
    }

    /**
     * 参数定义 JSON 文本转对象列表（空白或非法时返回空列表）。
     *
     * @param json JSON 数组文本（可为 null/空/非法）
     * @return 参数定义列表
     */
    public static List<ApiParamDef> parseParams(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return Json.fromJsonToList(json, ApiParamDef.class);
        } catch (Exception e) {
            return List.of();
        }
    }

    /**
     * 把参数定义序列化为 JSON 文本（保存用）。
     *
     * @param params 参数定义列表
     * @return JSON 文本；null 输入返回空文本
     */
    public static String toParamJson(List<ApiParamDef> params) {
        if (params == null || params.isEmpty()) {
            return "";
        }
        try {
            return Json.toJson(params);
        } catch (Exception e) {
            return "";
        }
    }
}