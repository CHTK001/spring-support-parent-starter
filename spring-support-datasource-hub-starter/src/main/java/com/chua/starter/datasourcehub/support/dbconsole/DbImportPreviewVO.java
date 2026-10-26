package com.chua.starter.datasourcehub.support.dbconsole;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;
import java.util.Map;

/**
 * 导入预览结果
 *
 * <p>导入向导第 2～3 步「选文件 + 附加选项」的回显：解析出前若干行与统计信息，供用户确认
 * 分隔符、编码与表头识别是否正确，再决定字段映射。</p>
 *
 * <p>第 2 步要显示「表」（Excel 工作表）多选，因此 {@code sheets} 与
 * {@code activeSheet} 也在这里回传：Excel 分支选完文件就能列出全部工作表，
 * 用户勾多张时前端再逐张预览。</p>
 *
 * @param columns      解析出的列名（按文件里的表头顺序）
 * @param rows         预览行，最多 {@code previewSize} 行
 * @param rowCount     文件内的数据总行数（不含表头）
 * @param previewSize  预览行数上限
 * @param delimiter    实际使用的分隔符
 * @param encoding     实际使用的字符集
 * @param format       实际使用的格式（auto 已解析成 csv / tsv / excel / json / sql）
 * @param sheets       工作表名列表（Excel 才有；其余格式为空）
 * @param activeSheet  本次解析的工作表名（Excel 才有）
 * @param warnings     解析告警（如「行头识别为第 2 行，请确认」），非致命
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
        String encoding,
        String format,
        List<String> sheets,
        String activeSheet,
        List<String> warnings
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
     * @param format      格式
     * @param sheets      工作表列表
     * @param activeSheet 当前工作表
     * @param warnings    解析告警
     */
    public DbImportPreviewVO {
        columns = columns == null ? List.of() : List.copyOf(columns);
        rows = rows == null ? List.of() : List.copyOf(rows);
        sheets = sheets == null ? List.of() : List.copyOf(sheets);
        warnings = warnings == null ? List.of() : List.copyOf(warnings);
    }

    /**
     * 兼容旧签名（无格式 / 工作表 / 告警）。
     *
     * @param columns     列名
     * @param rows        预览行
     * @param rowCount    总行数
     * @param previewSize 预览上限
     * @param delimiter   分隔符
     * @param encoding    字符集
     */
    public DbImportPreviewVO(List<String> columns, List<Map<String, Object>> rows, int rowCount,
                             int previewSize, String delimiter, String encoding) {
        this(columns, rows, rowCount, previewSize, delimiter, encoding, null, List.of(), null, List.of());
    }
}
