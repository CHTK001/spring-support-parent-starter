package com.chua.starter.datasourcehub.support.dbconsole;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;
import java.util.Map;

/**
 * 数据导入请求（对齐 Navicat 导入向导第 2～7 步的全部选项）。
 *
 * <p>{@code columnMapping} 的键是文件里的表头、值是目标表的列名；未列出的源列会被忽略。
 * 其余选项按向导步骤归组：</p>
 *
 * <ul>
 *   <li>第 2 步（选文件）：{@code fileName} / {@code format} / {@code encoding} / {@code sheetName}</li>
 *   <li>第 3 步（附加选项）：{@code recordDelimiter} / {@code delimiter} / {@code textQualifier}
 *       / {@code fieldNamesLine} / {@code firstDataLine} / {@code lastDataLine} / {@code decimalSymbol}</li>
 *   <li>第 5 步（字段映射）：{@code columnMapping} / {@code keyColumns}</li>
 *   <li>第 6 步（导入模式）：{@code mode} / 高级选项</li>
 *   <li>第 7 步（执行）：{@code batchSize} / {@code createTable} / {@code truncateFirst}</li>
 * </ul>
 *
 * @param fileName         上传文件名（仅用于回显与后缀探测）
 * @param format           格式：auto / csv / tsv / excel / json / sql
 * @param delimiter        字段分隔符字符，auto 时按内容探测；可为空
 * @param hasHeader        首行是否为表头（旧协议；新协议看 {@code fieldNamesLine}）
 * @param encoding         字符集名，为空按 UTF-8
 * @param database         目标库
 * @param tableName        目标表
 * @param columnMapping    源列 → 目标列映射
 * @param truncateFirst    导入前是否清空目标表
 * @param onConflict       旧协议的冲突策略：skip / replace（新协议看 {@code mode}）
 * @param batchSize        每批行数
 * @param datasourceId     数据源 ID
 * @param token            上传令牌（第 2 步上传后返回，第 3～7 步复用同一个文件）
 * @param sheetName        Excel 工作表名（format=excel 时有效）
 * @param recordDelimiter  记录分隔符：CRLF / LF / CR / 自定义
 * @param textQualifier    文本识别符（引号），空串表示不识别引号
 * @param fieldNamesLine   字段名称所在行号（1 起，0 = 文件没有表头）
 * @param firstDataLine    第一个数据行号（1 起）
 * @param lastDataLine     最后一个数据行号（1 起，0 = 读到末尾）
 * @param decimalSymbol    小数点符号
 * @param dateOrder        日期顺序：YMD / DMY / MDY（Excel 分支的日期选项）
 * @param dateTimeOrder    日期时间顺序：YMD / DMY / MDY
 * @param dateSeparator    日期分隔符（如 / - .）
 * @param yearSeparator    年份分隔符（如 / ，用于「2026年1月2日」）
 * @param timeSeparator    时间分隔符（如 :）
 * @param mode             导入模式：append / update / upsert / insert-ignore / delete / copy
 * @param keyColumns       用于匹配的键列（空 = 按表主键）
 * @param extendedInsert   是否使用扩展插入语句
 * @param maxStatementKb   最大语句大小（KB），0 = 不限制
 * @param nullForEmpty     空串是否写成 NULL
 * @param ignoreForeignKey 是否忽略外键约束
 * @param continueOnError  遇到错误是否继续
 * @param createTable      目标表不存在时是否按源列自动建表
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
        Long datasourceId,
        String sheetName,
        String recordDelimiter,
        String textQualifier,
        Integer fieldNamesLine,
        Integer firstDataLine,
        Integer lastDataLine,
        String decimalSymbol,
        String dateOrder,
        String dateTimeOrder,
        String dateSeparator,
        String yearSeparator,
        String timeSeparator,
        String mode,
        List<String> keyColumns,
        Boolean extendedInsert,
        Integer maxStatementKb,
        Boolean nullForEmpty,
        Boolean ignoreForeignKey,
        Boolean continueOnError,
        Boolean createTable,
        String token
) {
    /**
     * 紧凑构造器：规整布尔项与集合，避免前端传 null 时各处再判空。
     */
    public DbImportDTO {
        hasHeader = !Boolean.FALSE.equals(hasHeader);
        truncateFirst = Boolean.TRUE.equals(truncateFirst);
        keyColumns = keyColumns == null ? List.of() : List.copyOf(keyColumns);
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

    /**
     * 是否是 Excel 分支。
     *
     * @return true=Excel
     */
    public boolean excel() {
        return format != null && "excel".equalsIgnoreCase(format.trim());
    }

    /**
     * 导入模式。
     *
     * <p>新协议优先用 {@code mode}；旧协议只有 {@code onConflict}（skip / replace），
     * 由 {@link ImportMode#of(String)} 内部做兼容映射。</p>
     *
     * @return 导入模式
     */
    public ImportMode importMode() {
        return ImportMode.of(mode != null && !mode.isBlank() ? mode : onConflict);
    }

    /**
     * 高级选项。
     *
     * @return 高级选项；未传的项按 Navicat 出厂默认
     */
    public ImportAdvanced advanced() {
        ImportAdvanced defaults = ImportAdvanced.defaults();
        return new ImportAdvanced(
                extendedInsert == null ? defaults.extendedInsert() : extendedInsert,
                maxStatementKb == null ? defaults.maxStatementKb() : maxStatementKb,
                nullForEmpty == null ? defaults.nullForEmpty() : nullForEmpty,
                ignoreForeignKey == null ? defaults.ignoreForeignKey() : ignoreForeignKey,
                continueOnError == null ? defaults.continueOnError() : continueOnError);
    }

    /**
     * 日期解读规则（第 3 步 Excel 分支的日期选项）。
     *
     * @return 日期选项
     */
    public DateOptions dateOptions() {
        return new DateOptions(dateOrder, dateTimeOrder,
                firstChar(dateSeparator, '/'), firstChar(yearSeparator, '/'),
                firstChar(timeSeparator, ':'));
    }

    /**
     * 取首个字符；空串回退到默认值。
     *
     * @param value    原值
     * @param fallback 默认字符
     * @return 字符
     */
    private static char firstChar(String value, char fallback) {
        return value == null || value.isEmpty() ? fallback : value.charAt(0);
    }

    /**
     * 分隔符解析选项（第 3 步的附加选项）。
     *
     * <p>旧协议没传行号时，用 {@code hasHeader} 推导：有表头 = 第 1 行表头、第 2 行起数据。</p>
     *
     * @param detectedDelimiter 探测出的分隔符（选项里没指定时使用）
     * @return 解析选项
     */
    public DelimitedOptions delimitedOptions(char detectedDelimiter) {
        char fieldDelimiter = delimiterChar() == 0 ? detectedDelimiter : delimiterChar();
        int namesLine = fieldNamesLine != null ? fieldNamesLine
                : (Boolean.FALSE.equals(hasHeader) ? 0 : 1);
        int firstLine = firstDataLine != null ? firstDataLine : (namesLine > 0 ? namesLine + 1 : 1);
        int lastLine = lastDataLine == null ? 0 : lastDataLine;
        char qualifier = textQualifier == null
                ? DelimitedOptions.DEFAULT_TEXT_QUALIFIER
                : (textQualifier.isEmpty() ? 0 : textQualifier.charAt(0));
        char decimal = decimalSymbol == null || decimalSymbol.isEmpty() ? '.' : decimalSymbol.charAt(0);
        String record = recordDelimiter == null || recordDelimiter.isBlank()
                ? DelimitedOptions.DEFAULT_RECORD_DELIMITER : recordDelimiter;
        return new DelimitedOptions(fieldDelimiter, record, qualifier, namesLine, firstLine, lastLine,
                decimal, namesLine > 0);
    }

    /**
     * 批行数；未指定用隐含默认（扩展插入关闭时由
     * {@link ImportAdvanced#effectiveBatchRows(int)} 收敛为 1）。
     *
     * @return 批行数
     */
    public int batchRows() {
        return batchSize == null ? 0 : batchSize;
    }
}
