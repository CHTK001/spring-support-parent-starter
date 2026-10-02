package com.chua.starter.monitor.support.model.dto;

import com.chua.starter.monitor.support.model.SyncFieldMappingVO;

import java.util.List;

/**
 * 字段映射预览入参。
 *
 * <p>只读操作，不落库：给界面「点一下看配对」用。与保存接口分开，是因为预览时源/目标
 * 数据源与目标表都还没保存，拿不到任务 ID。</p>
 *
 * @param sourceDatasourceId 源数据源 ID
 * @param sourceSql          源读取 SQL，必须是 SELECT
 * @param targetDatasourceId 目标数据源 ID
 * @param targetTable        目标表名
 * @param mapping            界面上已配的映射；为 空 或空列表时按同名自动配对
 * @author CH
 * @since 4.0.0.43
 */
public record SyncPreviewDTO(
        Long sourceDatasourceId,
        String sourceSql,
        Long targetDatasourceId,
        String targetTable,
        List<SyncFieldMappingVO> mapping) {
}