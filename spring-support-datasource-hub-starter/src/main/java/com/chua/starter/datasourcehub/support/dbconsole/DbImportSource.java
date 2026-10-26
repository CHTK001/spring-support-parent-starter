package com.chua.starter.datasourcehub.support.dbconsole;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 导入源解析编排：把「文件字节 + 向导选项」变成统一的「表头 + 数据行」。
 *
 * <p>导入向导的两个分支（CSV 类文本 / Excel）在这里合流。合流点很关键：
 * 第 4～7 步（目标表、字段映射、导入模式、执行）只认 {@link DbTransferCodec.ParsedTable}，
 * 完全不关心数据是从分隔符文件来的还是从工作簿来的。这保证了「Excel 与 CSV 走同一套
 * 映射与写入逻辑」，也保证了两边对「字段名称行 / 第一个数据行 / 最后一个数据行」的
 * 解释完全一致（同一段 {@link DelimitedOptions#toTable} 收尾）。</p>
 *
 * @author CH
 * @since 4.0.0.45
 */
public final class DbImportSource {

    /** 解析结果 */
    public record Parsed(
            String format,
            List<String> sheets,
            String activeSheet,
            String delimiter,
            String encoding,
            DbTransferCodec.ParsedTable table,
            List<String> warnings
    ) {
        /**
         * 规范构造器：集合做防御性拷贝。
         *
         * @param format      实际格式
         * @param sheets      工作表列表
         * @param activeSheet 当前工作表
         * @param delimiter   分隔符（展示用）
         * @param encoding    字符集
         * @param table       解析结果
         * @param warnings    告警
         */
        public Parsed {
            sheets = sheets == null ? List.of() : List.copyOf(sheets);
            warnings = warnings == null ? List.of() : List.copyOf(warnings);
        }
    }

    private DbImportSource() {
    }

    /**
     * 解析文件。
     *
     * @param bytes  文件字节
     * @param option 向导选项（格式 / 编码 / 工作表 / 分隔符 / 行区间 / 小数点）
     * @return 解析结果
     * @throws IOException 文件损坏、格式不支持
     */
    public static Parsed parse(byte[] bytes, DbImportDTO option) throws IOException {
        String format = resolveFormat(option.format(), option.fileName(), bytes);
        List<String> warnings = new ArrayList<>();
        if ("excel".equals(format)) {
            if (!DbImportExcel.available()) {
                throw new IOException("当前实例未装配 Excel 解析能力（classpath 上缺少 Apache POI），请改用 CSV 导入");
            }
            List<String> sheets = DbImportExcel.sheetNames(bytes);
            String activeSheet = option.sheetName();
            if (activeSheet == null || activeSheet.isBlank()) {
                activeSheet = sheets.isEmpty() ? null : sheets.get(0);
            }
            List<List<String>> matrix = DbImportExcel.read(bytes, activeSheet);
            DbTransferCodec.ParsedTable table = option.delimitedOptions(',') == null
                    ? new DbTransferCodec.ParsedTable(List.of(), List.of())
                    : option.delimitedOptions(',').toTable(matrix);
            if (table.rows().isEmpty()) {
                warnings.add("工作表「" + activeSheet + "」在指定行区间内没有数据，请检查「第一个数据行」");
            }
            return new Parsed(format, sheets, activeSheet, "", option.encoding() == null ? "UTF-8" : option.encoding(),
                    table, warnings);
        }
        String text = DbTransferCodec.decode(bytes, option.encoding());
        char delimiter = resolveDelimiter(format, option, text);
        DbTransferCodec.ParsedTable table;
        if ("json".equals(format)) {
            table = DbTransferCodec.parseJson(text, List.of());
        } else if ("sql".equals(format)) {
            if (option.tableName() == null || option.tableName().isBlank()) {
                throw new IOException("SQL 脚本导入需要在第 4 步指定目标表");
            }
            table = new DbTransferCodec.ParsedTable(List.of(), List.of());
            warnings.add("SQL 脚本按语句整体执行，不参与字段映射");
        } else {
            DelimitedOptions options = option.delimitedOptions(delimiter);
            table = DbTransferCodec.parseDelimited(text, options);
            if (table.columns().size() > 0 && table.rows().isEmpty()) {
                warnings.add("字段名称行之后没有读到任何数据行，请检查「第一个数据行 / 最后一个数据行」");
            }
        }
        return new Parsed(format, List.of(), null, delimiter == '\t' ? "\\t" : String.valueOf(delimiter),
                DbTransferCodec.charsetName(option.encoding()), table, warnings);
    }

    /**
     * 解析实际格式（auto 时按后缀与内容探测）。
     *
     * @param format   选项里的格式
     * @param fileName 文件名
     * @param bytes    内容（探测用）
     * @return csv / tsv / json / sql / excel
     */
    public static String resolveFormat(String format, String fileName, byte[] bytes) {
        String explicit = format == null ? "" : format.trim().toLowerCase(Locale.ROOT);
        if (!explicit.isEmpty() && !"auto".equals(explicit)) {
            // 前端把 excel 传成 xls/xlsx 也认
            if ("xls".equals(explicit) || "xlsx".equals(explicit)) {
                return "excel";
            }
            return explicit;
        }
        String name = fileName == null ? "" : fileName.toLowerCase(Locale.ROOT);
        if (name.endsWith(".xls") || name.endsWith(".xlsx") || name.endsWith(".xlsm")) {
            return "excel";
        }
        if (name.endsWith(".sql")) {
            return "sql";
        }
        if (name.endsWith(".json")) {
            return "json";
        }
        if (name.endsWith(".tsv") || name.endsWith(".tab")) {
            return "tsv";
        }
        if (name.endsWith(".csv")) {
            return "csv";
        }
        String sample = DbTransferCodec.decode(bytes, null).stripLeading();
        if (sample.startsWith("[") || sample.startsWith("{")) {
            return "json";
        }
        if (sample.toUpperCase(Locale.ROOT).startsWith("INSERT") || sample.toUpperCase(Locale.ROOT).startsWith("CREATE")) {
            return "sql";
        }
        return "csv";
    }

    /**
     * 判定分隔符。
     *
     * <p>选项显式指定优先（用户在向导里选了「分号」就必须按分号，不能因为内容里逗号更多
     * 就改判）；否则 TSV 固定制表符，其余按内容探测。</p>
     *
     * @param format 格式
     * @param option 向导选项
     * @param text   文本
     * @return 分隔符
     */
    private static char resolveDelimiter(String format, DbImportDTO option, String text) {
        if (option.delimiterChar() != 0) {
            return option.delimiterChar();
        }
        if ("tsv".equals(format)) {
            return '\t';
        }
        return DbTransferCodec.detectDelimiter(text);
    }
}
