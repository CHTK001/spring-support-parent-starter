package com.chua.starter.monitor.support.model.vo;

import java.util.List;

/**
 * 一个同步目标的**生效配置**（由任务的多目标 JSON 或旧单目标字段归一化而来）。
 *
 * <p>与入参 DTO 分开：这里带上服务端独有的东西（独立水位 {@code lastSeq}），
 * 且所有「可空默认」都已经填好，执行链路拿到它就能直接用，不必再判空。</p>
 *
 * @param targetDatasourceId 目标数据源 ID
 * @param targetTable        目标表名（库.表）
 * @param writeMode          写入模式编码
 * @param syncMode           同步模式编码
 * @param incFields          增量字段（按顺序，复合水位）；全量为空列表
 * @param incBatchSize       增量每批数量，null 表示不限
 * @param columnMapping      字段映射 JSON，空表示自动配对
 * @param batchSize          批量提交条数
 * @param lastSeq            该目标自己的增量水位（复合水位用 JSON 数组串）
 * @author CH
 * @since 4.0.0.44
 */
public record SyncTargetPlan(
        Long targetDatasourceId,
        String targetTable,
        String writeMode,
        String syncMode,
        List<String> incFields,
        Integer incBatchSize,
        String columnMapping,
        Integer batchSize,
        String lastSeq) {

    /**
     * 是否增量目标。
     *
     * @return 增量返回 true
     */
    public boolean incremental() {
        return "INCREMENTAL".equalsIgnoreCase(syncMode);
    }

    /**
     * 目标展示名（日志 / 结果里用「库.表」，不带数据源）。
     *
     * @return 表名
     */
    public String label() {
        return targetTable == null ? "" : targetTable;
    }
}
