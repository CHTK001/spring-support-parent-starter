package com.chua.starter.monitor.support.model.dto;

import com.chua.starter.monitor.support.model.SyncFieldMappingVO;

import java.util.List;

/**
 * 字段映射预览入参。
 *
 * <p>只读操作，不落库：给界面「点一下看配对」用。与保存接口分开，是因为预览时源/目标
 * 数据源与目标表都还没保存，拿不到任务 ID。</p>
 *
 * <p>输入源同样分 JDBC / FILE / DIR：JDBC 用 {@code sourceDatasourceId + sourceSql}；
 * 文件与目录用 {@code sourcePath + sourceOptions}，源列直接取文件表头（csv 首行 / excel 首个非空行）。</p>
 *
 * @param sourceType         输入源类型 JDBC/FILE/DIR，空按 JDBC
 * @param sourceDatasourceId 源数据源 ID（JDBC 必填）
 * @param sourceSql          源读取 SQL（JDBC 必填）
 * @param sourcePath         文件/目录路径（FILE/DIR 必填）
 * @param sourceOptions      文件/目录源选项 JSON
 * @param targetDatasourceId 目标数据源 ID
 * @param targetTable        目标表名
 * @param mapping            界面上已配的映射；为 空 或空列表时按同名自动配对
 * @author CH
 * @since 4.0.0.43
 */
public record SyncPreviewDTO(
        String sourceType,
        Long sourceDatasourceId,
        String sourceSql,
        String sourcePath,
        String sourceOptions,
        Long targetDatasourceId,
        String targetTable,
        List<SyncFieldMappingVO> mapping) {
}