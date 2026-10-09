package com.chua.starter.datasourcehub.support.model;

/**
 * Prometheus 自定义看板视图。
 *
 * @param datasourceId 数据源 id
 * @param name         看板名称
 * @param config       布局配置（JSON 文本）
 * @param embedToken   嵌入令牌，空串表示尚未生成
 * @param updateTime   最近更新时间（yyyy-MM-dd HH:mm:ss），未保存过时为 null
 * @author CH
 * @since 4.0.0.42
 */
public record PrometheusDashboardVO(
        Long datasourceId,
        String name,
        String config,
        String embedToken,
        String updateTime
) {
}
