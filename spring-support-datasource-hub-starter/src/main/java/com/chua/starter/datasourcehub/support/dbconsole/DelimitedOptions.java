package com.chua.starter.datasourcehub.support.dbconsole;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 分隔符文件的解析选项（对齐 Navicat 第 3～4 步的「附加选项」）。
 *
 * <p>这些选项在 Navicat 里分散在两步：第 3 步是「行/日期/小数格式」，第 4 步是
 * 「记录分隔符 / 字段分隔符 / 文本识别符」。它们的共同点是<b>只影响把文件摊平成
 * 行列的方式</b>，不影响字段映射与写库，因此统一收在这个记录里，
 * 由 {@link DbTransferCodec#parseDelimited(String, DelimitedOptions)} 消费。</p>
 *
 * <p><b>为什么不用建造者</b>：字段全是可选且都有明确默认值，用 8 个参数的规范
 * 构造器 + 一个 {@link #defaults()} 静默更直观；调用方大多只改 1～2 项，
 * 靠 {@code with*} 方法链式覆盖即可，不需要引入额外类型。</p>
 *
 * @param delimiter       字段分隔符
 * @param recordDelimiter 记录（行）分隔符：{@code \r\n} / {@code \n} / {@code \r} / 自定义
 * @param textQualifier   文本识别符（引号），空表示不做引号处理
 * @param fieldNamesLine  字段名称所在行号（1 起，0 表示文件没有表头）
 * @param firstDataLine   第一个数据行号（1 起）
 * @param lastDataLine    最后一个数据行号（1 起，0 表示读到文件末尾）
 * @param decimalSymbol   小数点符号，用于把 {@code ,} 形式的小数统一成 {@code .}
 * @param hasHeader       首行是否表头（与 {@code fieldNamesLine > 0} 同义，保留以便调用方少算一次）
 * @author CH
 * @since 4.0.0.45
 */
public record DelimitedOptions(
        char delimiter,
        String recordDelimiter,
        char textQualifier,
        int fieldNamesLine,
        int firstDataLine,
        int lastDataLine,
        char decimalSymbol,
        boolean hasHeader
) {

    /** 默认记录分隔符：CRLF 是 Windows 导出 CSV 的常态，解析时 LF/CR 也一并认 */
    public static final String DEFAULT_RECORD_DELIMITER = "\r\n";

    /** 默认文本识别符 */
    public static final char DEFAULT_TEXT_QUALIFIER = '"';

    /**
     * 默认选项：逗号分隔、CRLF、双引号识别、首行表头、读到末尾、点号小数。
     *
     * @return 默认解析选项
     */
    public static DelimitedOptions defaults() {
        return new DelimitedOptions(',', DEFAULT_RECORD_DELIMITER, DEFAULT_TEXT_QUALIFIER, 1, 2, 0, '.', true);
    }

    /**
     * 规范构造器：把越界与空值收敛成有效值。
     *
     * <p>向导里的数字框可以清空（得到 0 或 null），也可能填负数。这里统一夹紧：
     * 行号小于 1 一律按「未指定」处理，避免下游拿到 -3 这种值去算下标。</p>
     *
     * @param delimiter       字段分隔符
     * @param recordDelimiter 记录分隔符
     * @param textQualifier   文本识别符
     * @param fieldNamesLine  字段名称行号
     * @param firstDataLine   第一数据行号
     * @param lastDataLine    最后数据行号
     * @param decimalSymbol   小数点符号
     * @param hasHeader       是否有表头
     */
    public DelimitedOptions {
        if (recordDelimiter == null) {
            recordDelimiter = DEFAULT_RECORD_DELIMITER;
        }
        if (fieldNamesLine < 0) {
            fieldNamesLine = 0;
        }
        if (firstDataLine < 1) {
            firstDataLine = 1;
        }
        if (lastDataLine < 0) {
            lastDataLine = 0;
        }
        // decimalSymbol 为 0 表示未指定，按点号
        if (decimalSymbol == 0) {
            decimalSymbol = '.';
        }
        // 表头判据以行号为准：fieldNamesLine=0 等价「没有表头」
        hasHeader = fieldNamesLine > 0;
    }

    /**
     * 覆盖字段分隔符。
     *
     * @param value 分隔符
     * @return 新选项
     */
    public DelimitedOptions withDelimiter(char value) {
        return new DelimitedOptions(value, recordDelimiter, textQualifier, fieldNamesLine,
                firstDataLine, lastDataLine, decimalSymbol, hasHeader);
    }

    /**
     * 覆盖记录分隔符。
     *
     * @param value 记录分隔符
     * @return 新选项
     */
    public DelimitedOptions withRecordDelimiter(String value) {
        return new DelimitedOptions(delimiter, value, textQualifier, fieldNamesLine,
                firstDataLine, lastDataLine, decimalSymbol, hasHeader);
    }

    /**
     * 覆盖文本识别符。
     *
     * @param value 识别符，0 表示不做引号处理
     * @return 新选项
     */
    public DelimitedOptions withTextQualifier(char value) {
        return new DelimitedOptions(delimiter, recordDelimiter, value, fieldNamesLine,
                firstDataLine, lastDataLine, decimalSymbol, hasHeader);
    }

    /**
     * 覆盖行区间。
     *
     * @param namesLine 字段名称行号，0 表示无表头
     * @param firstLine 第一数据行号
     * @param lastLine  最后数据行号，0 表示读到末尾
     * @return 新选项
     */
    public DelimitedOptions withLines(int namesLine, int firstLine, int lastLine) {
        return new DelimitedOptions(delimiter, recordDelimiter, textQualifier, namesLine,
                firstLine, lastLine, decimalSymbol, namesLine > 0);
    }

    /**
     * 覆盖小数点符号。
     *
     * @param value 小数点符号
     * @return 新选项
     */
    public DelimitedOptions withDecimalSymbol(char value) {
        return new DelimitedOptions(delimiter, recordDelimiter, textQualifier, fieldNamesLine,
                firstDataLine, lastDataLine, value, hasHeader);
    }

    /**
     * 归一化后的记录分隔符字符集。
     *
     * <p>{@code "\r\n"} 必须同时认 {@code \r} 与 {@code \n}：一个标称为 CRLF 的文件
     * 里混进几条纯 LF 行非常常见（手工编辑过），严格按两字符匹配会把整段内容读成一行。</p>
     *
     * @return 视为记录边界的字符数组
     */
    public char[] recordSeparatorChars() {
        String value = recordDelimiter == null ? DEFAULT_RECORD_DELIMITER : recordDelimiter;
        // 兼容按名字传的分隔符（"CRLF" / "LF" / "CR"）：不认名字的话，
        // "CRLF" 会被当成 C、R、L、F 四个自定义边界，整个文件读成一行。
        value = switch (value.trim().toUpperCase(java.util.Locale.ROOT)) {
            case "CRLF", "CR+LF" -> "\r\n";
            case "LF" -> "\n";
            case "CR" -> "\r";
            default -> value;
        };
        List<Character> chars = new ArrayList<>(3);
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if ((c == '\r' || c == '\n') && !chars.contains(c)) {
                chars.add(c);
            }
        }
        if (chars.isEmpty()) {
            // 自定义记录分隔符（如 "|"）：只认它本身
            char[] custom = value.toCharArray();
            char[] out = new char[custom.length];
            for (int i = 0; i < custom.length; i++) {
                out[i] = custom[i];
            }
            return out;
        }
        char[] out = new char[chars.size()];
        for (int i = 0; i < chars.size(); i++) {
            out[i] = chars.get(i);
        }
        return out;
    }

    /**
     * 自适应版本（兼容既有的两参调用）。
     *
     * @param delimiter 字段分隔符
     * @param hasHeader 是否有表头
     * @return 选项
     */
    public static DelimitedOptions of(char delimiter, boolean hasHeader) {
        return new DelimitedOptions(delimiter, DEFAULT_RECORD_DELIMITER, DEFAULT_TEXT_QUALIFIER,
                hasHeader ? 1 : 0, hasHeader ? 2 : 1, 0, '.', hasHeader);
    }

    /**
     * 把二维文本按行区间与引号规则解析成「表头 + 数据行」。
     *
     * <p>与 {@link DbTransferCodec#parseDelimited(String, char, boolean)} 的区别：
     * 这里先按行切（尊重引号内的换行），再按行区间截取，最后按分隔符切列 ——
     * 顺序不能反：先切列会把引号内的换行当成两个半行。</p>
     *
     * @param matrix 已按记录分隔符与文本识别符切好的二维矩阵（首元素为第 1 行）
     * @return 表头与数据行
     */
    public DbTransferCodec.ParsedTable toTable(List<List<String>> matrix) {
        List<String> header = new ArrayList<>();
        int namesIndex = fieldNamesLine <= 0 ? -1 : fieldNamesLine - 1;
        // 列数取全表最宽行：表头行可能比数据行短（末尾有空的表头单元格）
        int width = 0;
        for (List<String> row : matrix) {
            width = Math.max(width, row.size());
        }
        if (namesIndex >= 0 && namesIndex < matrix.size()) {
            List<String> names = matrix.get(namesIndex);
            for (int c = 0; c < width; c++) {
                String name = c < names.size() ? names.get(c).trim() : "";
                // 空表头补 colN，否则整列的行 Map 会共用 "" 键互相覆盖
                header.add(name.isEmpty() ? "col" + (c + 1) : name);
            }
        } else {
            for (int c = 0; c < width; c++) {
                header.add("col" + (c + 1));
            }
        }
        // 去重：同名表头（常见于合并单元格展开后）会让行 Map 丢列
        Map<String, Integer> seen = new LinkedHashMap<>();
        for (int c = 0; c < header.size(); c++) {
            String name = header.get(c);
            Integer count = seen.get(name);
            if (count == null) {
                seen.put(name, 1);
            } else {
                seen.put(name, count + 1);
                header.set(c, name + "_" + count);
            }
        }
        int from = Math.max(0, firstDataLine - 1);
        int to = lastDataLine <= 0 ? matrix.size() : Math.min(matrix.size(), lastDataLine);
        List<Map<String, Object>> rows = new ArrayList<>();
        for (int r = from; r < to; r++) {
            List<String> record = matrix.get(r);
            boolean blank = record.stream().allMatch(v -> v == null || v.trim().isEmpty());
            if (blank) {
                continue;
            }
            Map<String, Object> row = new LinkedHashMap<>();
            for (int c = 0; c < header.size(); c++) {
                String value = c < record.size() ? record.get(c) : null;
                row.put(header.get(c), normalizeDecimal(value));
            }
            rows.add(row);
        }
        return new DbTransferCodec.ParsedTable(List.copyOf(header), rows);
    }

    /**
     * 按自定义小数点符号归一化数值文本。
     *
     * <p>只处理「整串是一个数」的情形：{@code 1,25} → {@code 1.25}。段落文本里的
     * 逗号（{@code a,b}）不动 —— 这决定了必须用「全串解析成功才替换」而不是
     * 无脑 {@code String.replace}，后者会把备注里的逗号也换成点。</p>
     *
     * <p>千分位必须一起处理：欧洲写法 {@code 1.250,75} 里逗号是小数点、点是千分位，
     * 只把逗号换成点会得到 {@code 1.250.75}，直接解析失败。这里按
     * 「符号 + 整数部分（可分组）+ 小数部分」逐段校验，任一段不合规就原样返回，
     * 绝不猜。</p>
     *
     * @param value 原始单元格文本
     * @return 归一化后的文本
     */
    private String normalizeDecimal(String value) {
        // 点小数是默认值：此时不动任何文本。{@code 1,250} 到底是 1250 还是 1.25
        // 取决于目标列与用户意图，解析阶段擅自改写风险远大于收益
        if (value == null || decimalSymbol == '.' || value.isBlank()) {
            return value;
        }
        char groupSymbol = '.';
        String trimmed = value.trim();
        if (trimmed.indexOf(decimalSymbol) < 0) {
            // 用户指定了逗号小数但这串里没有逗号：原样交给数据库判断
            return value;
        }
        String sign = "";
        String body = trimmed;
        if (body.startsWith("+") || body.startsWith("-")) {
            sign = body.substring(0, 1);
            body = body.substring(1);
        }
        if (body.isEmpty()) {
            return value;
        }
        String fraction = "";
        int dot = body.indexOf(decimalSymbol);
        if (dot >= 0) {
            // 小数点出现两次以上说明不是数（如 1,2,3）
            if (body.indexOf(decimalSymbol, dot + 1) >= 0) {
                return value;
            }
            fraction = body.substring(dot + 1);
            body = body.substring(0, dot);
            if (fraction.isEmpty() || !isDigits(fraction)) {
                return value;
            }
        }
        if (body.isEmpty()) {
            return value;
        }
        String[] groups = body.split(java.util.regex.Pattern.quote(String.valueOf(groupSymbol)), -1);
        for (int i = 0; i < groups.length; i++) {
            if (!isDigits(groups[i])) {
                return value;
            }
            // 首段 1～3 位，其余必须正好 3 位：1.250 是 1250，1.25 不是数
            boolean validLength = i == 0 ? groups[i].length() <= 3 : groups[i].length() == 3;
            if (!validLength) {
                return value;
            }
        }
        String plain = sign + String.join("", groups) + (dot >= 0 ? "." + fraction : "");
        try {
            new java.math.BigDecimal(plain);
        } catch (NumberFormatException notANumber) {
            return value;
        }
        return plain;
    }

    /**
     * 是否全为 ASCII 数字且非空。
     *
     * @param text 待判定文本
     * @return true 表示只由数字组成
     */
    private static boolean isDigits(String text) {
        if (text == null || text.isEmpty()) {
            return false;
        }
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c < '0' || c > '9') {
                return false;
            }
        }
        return true;
    }
}
