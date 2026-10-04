package com.chua.starter.monitor.support.model.dto;

/**
 * 数据同步任务的新建/修改入参。
 *
 * <p>刻意不含「最近一次执行结果」相关字段：那些由服务端执行后回写，不接受调用方指定，
 * 否则界面可以伪造成功状态。</p>
 *
 * <p>输入源分三类（{@code sourceType}）：</p>
 * <ul>
 *   <li>{@code JDBC}：用 {@code sourceDatasourceId} + {@code sourceSql}（必须 SELECT）</li>
 *   <li>{@code FILE}：用 {@code sourcePath} 指向服务器本地的一个文件（csv / excel）</li>
 *   <li>{@code DIR}：用 {@code sourcePath} 指向服务器本地的一个目录，按 {@code sourceOptions.pattern}
 *       匹配文件；启用后由目录监听实时录入新文件</li>
 * </ul>
 *
 * @param name               任务名称，必填
 * @param sourceType         输入源类型 JDBC/FILE/DIR，空按 JDBC
 * @param sourceDatasourceId 源数据源 ID，{@code JDBC} 时必填且必须是 jdbc 类型
 * @param sourceSql          源读取 SQL，{@code JDBC} 时必填，必须以 SELECT 开头
 * @param sourcePath         文件/目录路径（服务器本地绝对路径），{@code FILE}/{@code DIR} 时必填
 * @param sourceOptions      文件/目录源选项 JSON：{@code {format,delimiter,sheetIndex,pattern,recursive,afterProcess}}
 * @param targetDatasourceId 目标数据源 ID，必填，且必须是 jdbc 类型
 * @param targetTable        目标表名，必填；只允许字母、数字、下划线，可带一段 {@code 库.表}
 * @param columnMapping      字段映射 JSON 数组，可为 空表示按同名自动匹配
 * @param writeMode          写入模式，可为 空，默认 {@code INSERT}
 * @param batchSize          批量提交条数，可为 空，默认 500；小于 1 时按 500 处理
 * @param cron               定时表达式（Spring 6 段 cron），可为 空表示只手动执行
 * @param enabled            是否启用定时，可为 空，默认 0
 * @author CH
 * @since 4.0.0.43
 */
public record SyncTaskDTO(
        String name,
        String sourceType,
        Long sourceDatasourceId,
        String sourceSql,
        String sourcePath,
        String sourceOptions,
        Long targetDatasourceId,
        String targetTable,
        String columnMapping,
        String writeMode,
        Integer batchSize,
        String cron,
        Integer enabled) {
}