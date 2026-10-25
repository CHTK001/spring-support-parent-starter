package com.chua.starter.monitor.support.model.dto;

import java.util.List;

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
 * @param targetDatasourceId 目标数据源 ID（多目标改造后**保留**为第一个目标的兼容值）
 * @param targetTable        目标表名（同上，保留为第一个目标的兼容值）
 * @param targets            多目标列表：一次采集写入多张表，每项一套独立配置；
 *                           为空时按旧单目标字段（targetDatasourceId + targetTable）解析
 * @param columnMapping      字段映射 JSON 数组，可为 空表示按同名自动匹配（多目标时按目标分别配置）
 * @param writeMode          写入模式，可为 空，默认 {@code INSERT}
 * @param batchSize          批量提交条数，可为 空，默认 500；小于 1 时按 500 处理
 * @param cron               定时表达式（Spring 6 段 cron），可为 空表示只手动执行
 * @param enabled            是否启用定时，可为 空，默认 0
 * @param syncMode           同步模式 FULL/INCREMENTAL，可为 空默认 FULL；增量仅 JDBC/HTTP 源可用
 * @param incField           增量字段，{@code INCREMENTAL} 时必填；多个字段按顺序用逗号分隔
 *                           （如 {@code created_at,id}），构成复合水位，只允许字母数字下划线
 * @param incBatchSize       增量每批数量（单次执行最多同步条数），可为 空表示不限
 * @param hookUrl            Hook 通知地址（http/https），可为 空表示不通知
 * @param hookNotify         Hook 通知时机 DONE/ROW，可为 空默认 DONE；全量模式固定按 DONE 处理
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
        List<SyncTargetDTO> targets,
        String columnMapping,
        String writeMode,
        Integer batchSize,
        String cron,
        Integer enabled,
        String syncMode,
        String incField,
        Integer incBatchSize,
        String hookUrl,
        String hookNotify) {
}