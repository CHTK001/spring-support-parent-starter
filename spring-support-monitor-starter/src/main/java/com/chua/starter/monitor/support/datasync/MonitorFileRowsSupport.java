package com.chua.starter.monitor.support.datasync;

import com.chua.datasync.agent.support.source.DelimitedDataSyncAgentSource;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import reactor.core.publisher.Flux;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.DecimalFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 文件输入源的行读取器（csv / excel）。
 *
 * <p>两条实现刻意分开：</p>
 * <ul>
 *   <li>CSV 直接复用引擎的 {@link DelimitedDataSyncAgentSource}——它已经处理了「首行表头、
 *       按定界符切分、流式逐行、异常落进 Flux」这几件事，本模块不重复造。</li>
 *   <li>Excel 引擎没有现成源，用 POI 读（{@code poi-ooxml} 本就在本模块 classpath 上）。
 *       仍然逐行 {@code sink.next}，不把整表先收进 List——与 {@code streamAndWrite}
 *       的流式约定保持一致，避免大表翻倍占内存。</li>
 * </ul>
 *
 * <p>两条路径产出的行都是 {@code Map<列名, 值>}，值统一成字符串（CSV 天然是字符串，
 * Excel 也按显示值转字符串），这样字段映射与写入端只需处理一种形状。</p>
 *
 * @author CH
 * @since 4.0.0.43
 */
@Slf4j
public final class MonitorFileRowsSupport {

    /**
     * 数值单元格去掉科学计数法与多余小数位时用的格式
     */
    private static final DecimalFormat NUMBER_FORMAT = new DecimalFormat("#.##");

    /**
     * 日期单元格转字符串的格式
     */
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /**
     * 工具类不允许实例化
     */
    private MonitorFileRowsSupport() {
    }

    /**
     * 读取文件全部行。
     *
     * @param file    文件路径
     * @param options 源选项（决定按 CSV 还是 Excel 解析）
     * @return 行流，每行是「列名 → 字符串值」
     */
    public static Flux<Map<String, Object>> read(Path file, SyncSourceOptions options) {
        SyncSourceOptions effective = options == null ? SyncSourceOptions.defaults() : options;
        if (!Files.isRegularFile(file)) {
            return Flux.error(new IllegalStateException("文件不存在或不是普通文件: " + file));
        }
        if ("EXCEL".equals(effective.formatOf(file))) {
            return readExcel(file, effective.normalized().sheetIndex());
        }
        return readCsv(file, effective.normalized().delimiter());
    }

    /**
     * 读取文件表头（预览源列用，不读数据行）。
     *
     * @param file    文件路径
     * @param options 源选项
     * @return 列名列表，取不到时为空列表
     */
    public static List<String> headers(Path file, SyncSourceOptions options) {
        SyncSourceOptions effective = options == null ? SyncSourceOptions.defaults() : options;
        if (!Files.isRegularFile(file)) {
            throw new IllegalStateException("文件不存在或不是普通文件: " + file);
        }
        try {
            return "EXCEL".equals(effective.formatOf(file))
                    ? excelHeaders(file, effective.normalized().sheetIndex())
                    : csvHeaders(file, effective.normalized().delimiter());
        } catch (Exception e) {
            throw new IllegalStateException("读取表头失败: " + file + " - " + e.getMessage(), e);
        }
    }

    /**
     * CSV：交给引擎的定界符源。
     *
     * @param file      文件
     * @param delimiter 定界符
     * @return 行流
     */
    private static Flux<Map<String, Object>> readCsv(Path file, String delimiter) {
        DelimitedDataSyncAgentSource source =
                new DelimitedDataSyncAgentSource("sync-file", "sync-file-in", file.toString(), delimiter);
        // params 不含 offset 即全量读；不调用 readOffset/writeOffset，避免动引擎的偏移量文件
        return source.read(new HashMap<>());
    }

    /**
     * CSV 首行表头。
     *
     * @param file      文件
     * @param delimiter 定界符
     * @return 列名
     * @throws Exception 读取失败
     */
    private static List<String> csvHeaders(Path file, String delimiter) throws Exception {
        try (var reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            String first = reader.readLine();
            if (first == null) {
                return List.of();
            }
            return List.of(first.split(java.util.regex.Pattern.quote(delimiter), -1));
        }
    }

    /**
     * Excel：逐行产出，工作簿在 Flux 内部开关，不在方法外持有。
     *
     * @param file       文件
     * @param sheetIndex 工作表序号（0 起）
     * @return 行流
     */
    private static Flux<Map<String, Object>> readExcel(Path file, int sheetIndex) {
        return Flux.create(sink -> {
            Workbook workbook = null;
            try {
                workbook = WorkbookFactory.create(file.toFile());
                if (sheetIndex >= workbook.getNumberOfSheets()) {
                    throw new IllegalStateException("工作表序号越界: " + sheetIndex
                            + "（该文件共 " + workbook.getNumberOfSheets() + " 个工作表）");
                }
                Sheet sheet = workbook.getSheetAt(sheetIndex);
                List<String> headers = null;
                for (Row row : sheet) {
                    if (headers == null) {
                        if (isBlankRow(row)) {
                            continue;
                        }
                        headers = headerOfRow(row);
                        continue;
                    }
                    if (isBlankRow(row)) {
                        continue;
                    }
                    sink.next(rowOf(row, headers));
                }
                if (headers == null) {
                    throw new IllegalStateException("工作表为空，找不到表头行: " + file);
                }
                sink.complete();
            } catch (Exception e) {
                sink.error(new IllegalStateException("Excel 读取失败: " + file + " - " + e.getMessage(), e));
            } finally {
                closeQuietly(workbook);
            }
        });
    }

    /**
     * Excel 表头。
     *
     * @param file       文件
     * @param sheetIndex 工作表序号
     * @return 列名
     * @throws Exception 读取失败
     */
    private static List<String> excelHeaders(Path file, int sheetIndex) throws Exception {
        try (Workbook workbook = WorkbookFactory.create(file.toFile())) {
            if (sheetIndex >= workbook.getNumberOfSheets()) {
                throw new IllegalStateException("工作表序号越界: " + sheetIndex);
            }
            Sheet sheet = workbook.getSheetAt(sheetIndex);
            for (Row row : sheet) {
                if (!isBlankRow(row)) {
                    return headerOfRow(row);
                }
            }
            throw new IllegalStateException("工作表为空，找不到表头行: " + file);
        }
    }

    /**
     * 取表头（空单元格退化为「第N列」）。
     *
     * @param row 表头行
     * @return 列名
     */
    private static List<String> headerOfRow(Row row) {
        List<String> headers = new ArrayList<>();
        int last = Math.max(row.getLastCellNum(), 0);
        for (int i = 0; i < last; i++) {
            String value = valueOf(row.getCell(i, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL));
            headers.add(value == null || value.isBlank() ? "第" + (i + 1) + "列" : value.trim());
        }
        return headers;
    }

    /**
     * 按表头取值成 Map。
     *
     * @param row     数据行
     * @param headers 表头
     * @return 行数据
     */
    private static Map<String, Object> rowOf(Row row, List<String> headers) {
        Map<String, Object> data = new LinkedHashMap<>();
        for (int i = 0; i < headers.size(); i++) {
            data.put(headers.get(i), valueOf(row.getCell(i, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL)));
        }
        return data;
    }

    /**
     * 单元格转字符串。
     *
     * @param cell 单元格，可为 null
     * @return 字符串值，空单元格返回 null
     */
    private static String valueOf(Cell cell) {
        if (cell == null) {
            return null;
        }
        CellType type = cell.getCellType();
        if (type == CellType.FORMULA) {
            type = cell.getCachedFormulaResultType();
        }
        return switch (type) {
            case STRING -> cell.getStringCellValue();
            case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
            case NUMERIC -> {
                if (DateUtil.isCellDateFormatted(cell)) {
                    LocalDateTime time = cell.getLocalDateTimeCellValue();
                    yield time == null ? null : time.format(DATE_FORMAT);
                }
                yield NUMBER_FORMAT.format(cell.getNumericCellValue());
            }
            default -> null;
        };
    }

    /**
     * 整行是否为空（全空单元格）。
     *
     * @param row 行，可为 null
     * @return true 表示空行
     */
    private static boolean isBlankRow(Row row) {
        if (row == null) {
            return true;
        }
        int last = Math.max(row.getLastCellNum(), 0);
        for (int i = 0; i < last; i++) {
            String value = valueOf(row.getCell(i, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL));
            if (value != null && !value.isBlank()) {
                return false;
            }
        }
        return true;
    }

    /**
     * 静默关闭工作簿。
     *
     * @param workbook 工作簿，可为 null
     */
    private static void closeQuietly(Workbook workbook) {
        if (workbook == null) {
            return;
        }
        try {
            workbook.close();
        } catch (Exception e) {
            log.debug("[sync-task] 关闭 Excel 工作簿失败: {}", e.getMessage());
        }
    }
}
