package com.chua.starter.monitor.support.model.dto;

import java.util.List;

/**
 * 多目标输出中的**一个**目标配置。
 *
 * <p>一次采集的结果可以分别写进多张表（例如同一份设备数据同时进明细表与归档表），
 * 因此「目标」从任务的单值字段升级成列表：每一项都是一套独立的写入配置，
 * 互不影响（独立事务、独立水位、独立失败）。</p>
 *
 * <p><b>字段与原单目标配置一一对应</b>：{@code targetDatasourceId} / {@code targetTable} /
 * {@code writeMode} / {@code syncMode} / {@code incFields} / {@code columnMapping} /
 * {@code batchSize} / {@code incBatchSize}。这样「每个目标都是现在的这些配置」成立，
 * 不需要为多目标发明第二套语义。</p>
 *
 * @param targetDatasourceId 目标数据源 ID，必填，必须是 jdbc 类型
 * @param targetTable        目标表名，必填；可带一段 {@code 库.表}
 * @param writeMode          写入模式 INSERT/REPLACE/UPSERT/OVERWRITE，空默认 INSERT
 * @param syncMode           同步模式 FULL/INCREMENTAL，空默认 FULL
 * @param incFields          增量字段，<b>按选择顺序生效</b>（复合水位）；全量时为空
 * @param columnMapping      字段映射 JSON 数组，空表示按同名自动匹配
 * @param batchSize          批量提交条数，空默认 500
 * @param incBatchSize       增量每批数量，空表示不限
 * @author CH
 * @since 4.0.0.44
 */
public record SyncTargetDTO(
        Long targetDatasourceId,
        String targetTable,
        String writeMode,
        String syncMode,
        List<String> incFields,
        String columnMapping,
        Integer batchSize,
        Integer incBatchSize) {
}
