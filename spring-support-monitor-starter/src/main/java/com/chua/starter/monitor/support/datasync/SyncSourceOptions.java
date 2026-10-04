package com.chua.starter.monitor.support.datasync;

import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.util.Arrays;
import java.util.Locale;

/**
 * 文件 / 目录输入源的扩展选项。
 *
 * <p>以 JSON 存进 {@code ms_sync_task.ms_sync_task_source_options}，字段刻意保持扁平，
 * 由界面按「格式」显隐对应项。所有字段都可空，读取时按这里的默认值补齐。</p>
 *
 * @param format      文件格式：{@code CSV} / {@code EXCEL}
 * @param delimiter   CSV 定界符，默认 {@code ,}（{@code TSV} 传制表符）
 * @param sheetIndex  Excel 工作表序号（0 起），默认 0
 * @param pattern     目录匹配规则，逗号分隔的 glob（如 {@code *.csv,*.xlsx}），默认 csv+xls/xlsx
 * @param recursive   目录是否递归子目录，默认 false
 * @param afterProcess 目录模式处理完单个文件的动作：{@code KEEP} / {@code ARCHIVE} / {@code DELETE}，默认 {@code KEEP}
 * @author CH
 * @since 4.0.0.43
 */
public record SyncSourceOptions(
        String format,
        String delimiter,
        Integer sheetIndex,
        String pattern,
        Boolean recursive,
        String afterProcess) {

    /**
     * 默认 CSV 定界符
     */
    public static final String DEFAULT_DELIMITER = ",";

    /**
     * 默认目录匹配规则（CSV + Excel）
     */
    public static final String DEFAULT_PATTERN = "*.csv,*.xls,*.xlsx";

    /**
     * 目录模式默认处理动作：保留原文件
     */
    public static final String AFTER_KEEP = "KEEP";

    /**
     * 目录模式处理动作：归档到 {@code done/} 子目录
     */
    public static final String AFTER_ARCHIVE = "ARCHIVE";

    /**
     * 目录模式处理动作：删除源文件
     */
    public static final String AFTER_DELETE = "DELETE";

    /**
     * 全默认值。
     *
     * @return 选项
     */
    public static SyncSourceOptions defaults() {
        return new SyncSourceOptions(null, null, null, null, null, null);
    }

    /**
     * 把 null / 空串补齐成默认值，避免调用方到处判空。
     *
     * @return 归一化后的选项
     */
    public SyncSourceOptions normalized() {
        return new SyncSourceOptions(
                isBlank(format) ? "CSV" : format.trim().toUpperCase(Locale.ROOT),
                isBlank(delimiter) ? DEFAULT_DELIMITER : delimiter,
                sheetIndex == null || sheetIndex < 0 ? 0 : sheetIndex,
                isBlank(pattern) ? DEFAULT_PATTERN : pattern.trim(),
                recursive != null && recursive,
                isBlank(afterProcess) ? AFTER_KEEP : afterProcess.trim().toUpperCase(Locale.ROOT));
    }

    /**
     * 是否 Excel 格式。
     *
     * @return true 表示按 Excel 解析
     */
    public boolean isExcel() {
        return "EXCEL".equals(normalized().format());
    }

    /**
     * 目录模式：判断文件是否命中匹配规则（按文件名 glob，大小写不敏感）。
     *
     * @param file 待判定文件
     * @return true 表示需要同步
     */
    public boolean matches(Path file) {
        String name = file.getFileName().toString().toLowerCase(Locale.ROOT);
        for (String glob : normalized().pattern().split(",")) {
            String item = glob.trim().toLowerCase(Locale.ROOT);
            if (item.isEmpty()) {
                continue;
            }
            PathMatcher matcher = FileSystems.getDefault().getPathMatcher("glob:" + item);
            if (matcher.matches(Path.of(name))) {
                return true;
            }
        }
        return false;
    }

    /**
     * 按扩展名推断格式（目录模式下一个任务可能同时匹配 csv 与 xlsx，逐文件判定）。
     *
     * @param file 待判定文件
     * @return 该文件应使用的格式，{@code EXCEL} 或 {@code CSV}
     */
    public String formatOf(Path file) {
        String name = file.getFileName().toString().toLowerCase(Locale.ROOT);
        boolean excel = Arrays.stream(new String[]{".xls", ".xlsx", ".xlsm"}).anyMatch(name::endsWith);
        return excel ? "EXCEL" : "CSV";
    }

    /**
     * 空串判断
     *
     * @param value 原值
     * @return true 表示 null 或空白
     */
    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
