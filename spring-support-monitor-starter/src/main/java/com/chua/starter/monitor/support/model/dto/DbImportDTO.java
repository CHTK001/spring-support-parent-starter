package com.chua.starter.monitor.support.model.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;
import java.util.Map;

/**
 * 数据导入请求
 *
 * <p>对应 Navicat 导入向导的一次执行：先选文件与格式，再逐列勾选/映射，最后提交。
 * {@code columnMapping} 的键是文件里的表头、值是目标表的列名；未列出的源列会被忽略。</p>
 *
 * @param fileName      上传文件名（仅用于回显与后缀探测）
 * @param format        格式：auto / csv / tsv / json / sql
 * @param delimiter     分隔符字符，auto 时按内容探测；可为空
 * @param hasHeader     首行是否为表头
 * @param encoding      字符集名，为空按 UTF-8
 * @param database      目标库
 * @param tableName     目标表
 * @param columnMapping 源列 → 目标列映射
 * @param truncateFirst 导入前是否清空目标表
 * @param onConflict    冲突策略：skip / replace
 * @param batchSize     每批行数
 * @param datasourceId  数据源 ID
 * @author CH
 * @since 4.0.0.43
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record DbImportDTO(
        String fileName,
        String format,
        String delimiter,
        Boolean hasHeader,
        String encoding,
        String database,
        String tableName,
        Map<String, String> columnMapping,
        Boolean truncateFirst,
        String onConflict,
        Integer batchSize,
        Long datasourceId
) {
    /**
     * 紧凑构造器：规整布尔项，避免前端传 null 时各处再判空。
     */
    public DbImportDTO {
        hasHeader = !Boolean.FALSE.equals(hasHeader);
        truncateFirst = Boolean.TRUE.equals(truncateFirst);
    }

    /**
     * 分隔符字符；未指定时按格式给默认值（auto 由内容探测）。
     *
     * @return 分隔符字符，解析失败返回 0 表示自动探测
     */
    public char delimiterChar() {
        if (delimiter == null || delimiter.isBlank()) {
            return 0;
        }
        return delimiter.charAt(0);
    }

    /**
     * 参与导入的源列名。
     *
     * @return 源列名列表；无映射时为空，表示按同名列自动对齐
     */
    public List<String> sourceColumns() {
        return columnMapping == null ? List.of() : List.copyOf(columnMapping.keySet());
    }
}