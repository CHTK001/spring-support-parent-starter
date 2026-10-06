package com.chua.starter.datasourcehub.support.dbconsole;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;
import java.util.Map;

/**
 * 导入预览结果
 *
 * <p>导入向导第一步「选文件」的回显：解析出前若干行与统计信息，供用户确认分隔符、
 * 编码与表头识别是否正确，再决定字段映射。</p>
 *
 * @param columns   解析出的列名（按文件里的表头顺序）
 * @param rows      预览行，最多 {@code previewSize} 行
 * @param rowCount  文件内的数据总行数（不含表头）
 * @param previewSize 预览行数上限
 * @param delimiter 实际使用的分隔符
 * @param encoding  实际使用的字符集
 * @author CH
 * @since 4.0.0.43
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record DbImportPreviewVO(
        List<String> columns,
        List<Map<String, Object>> rows,
        int rowCount,
        int previewSize,
        String delimiter,
        String encoding
) {
    /**
     * 规范构造器：集合做防御性拷贝。
     *
     * @param columns     列名
     * @param rows        预览行
     * @param rowCount    总行数
     * @param previewSize 预览上限
     * @param delimiter   分隔符
     * @param encoding    字符集
     */
    public DbImportPreviewVO {
        columns = columns == null ? List.of() : List.copyOf(columns);
        rows = rows == null ? List.of() : List.copyOf(rows);
    }
}