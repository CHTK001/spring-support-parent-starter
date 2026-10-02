package com.chua.starter.monitor.support.model.dto;

/**
 * 数据同步任务的新建/修改入参。
 *
 * <p>刻意不含「最近一次执行结果」相关字段：那些由服务端执行后回写，不接受调用方指定，
 * 否则界面可以伪造成功状态。</p>
 *
 * @param name             任务名称，必填
 * @param sourceDatasourceId 源数据源 ID，必填，且必须是 jdbc 类型
 * @param sourceSql        源读取 SQL，必填，必须以 SELECT 开头
 * @param targetDatasourceId 目标数据源 ID，必填，且必须是 jdbc 类型
 * @param targetTable      目标表名，必填；只允许字母、数字、下划线，可带一段 {@code 库.表}
 * @param columnMapping    字段映射 JSON 数组，可为 空表示按同名自动匹配
 * @param writeMode        写入模式，可为 空，默认 {@code INSERT}
 * @param batchSize        批量提交条数，可为 空，默认 500；小于 1 时按 500 处理
 * @param cron             定时表达式（Spring 6 段 cron），可为 空表示只手动执行
 * @param enabled          是否启用定时，可为 空，默认 0
 * @author CH
 * @since 4.0.0.43
 */
public record SyncTaskDTO(
        String name,
        Long sourceDatasourceId,
        String sourceSql,
        Long targetDatasourceId,
        String targetTable,
        String columnMapping,
        String writeMode,
        Integer batchSize,
        String cron,
        Integer enabled) {
}