package com.chua.starter.datasourcehub.support.dbconsole;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.FormulaEvaluator;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Excel（xls / xlsx）工作簿读取器。
 *
 * <p>只负责「把工作簿摊平成二维文本」，不做任何类型推断：单元格一律按
 * <b>显示值</b>取值（{@link CellType#NUMERIC} 走 {@link BigDecimal#stripTrailingZeros()}
 * 避免整数被读成 {@code 1.0}），日期单元格取 {@code yyyy-MM-dd HH:mm:ss}，
 * 公式取最后一次计算值。这样与用户「在 Excel 里看到什么就导入什么」的直觉一致，
 * 也与 CSV 分支得到同一种 {@code List<List<String>>} 形态，后续列映射无需分叉。</p>
 *
 * <p><b>为什么单独拆一个类</b>：POI 是 {@code optional} 依赖。宿主没引
 * {@code utils-support-word-starter} 时 classpath 上没有 POI，直接在本类里写
 * {@code import org.apache.poi.*} 会让 <b>类加载</b>阶段就抛
 * {@code NoClassDefFoundError}，连「该格式不可用」的友好提示都给不出来。
 * 因此 POI 的引用全部收在这个类里，由 {@link DbImportExcel#available()} 先探测、
 * 再由 {@link DbImportExcel} 决定要不要碰本类 —— 类只在真正用到时才初始化。</p>
 *
 * @author CH
 * @since 4.0.0.45
 */
final class DbExcelSheetReader {

    /** 日期时间单元格的输出格式，与 {@link DbTransferCodec} 的时间戳口径一致 */
    private static final String DATE_TIME_PATTERN = "yyyy-MM-dd HH:mm:ss";

    /** 单个工作簿允许读取的最大行数，防止超大表把堆打满（与 CSV 侧同口径） */
    private static final int MAX_ROWS = 500_000;

    private DbExcelSheetReader() {
    }

    /**
     * 列出工作簿里的工作表名。
     *
     * @param bytes 工作簿字节
     * @return 工作表名，按工作簿内顺序
     * @throws IOException 文件损坏或不是 Excel
     */
    static List<String> sheetNames(byte[] bytes) throws IOException {
        try (Workbook workbook = open(bytes)) {
            List<String> names = new ArrayList<>(workbook.getNumberOfSheets());
            for (int i = 0; i < workbook.getNumberOfSheets(); i++) {
                names.add(workbook.getSheetName(i));
            }
            return names;
        }
    }

    /**
     * 读取整张工作表为二维文本。
     *
     * <p>列数取「所有行里最大的 {@code lastCellNum}」：Excel 的行不保证等长，
     * 只按表头行取列数会把下方多出来的列截掉。缺失的格子按空串补齐，
     * 让下游的「按列下标取值」永远不会越界。</p>
     *
     * @param bytes     工作簿字节
     * @param sheetName 工作表名；为空取第一张
     * @return 行 × 列的文本矩阵
     * @throws IOException 文件损坏、工作表不存在
     */
    static List<List<String>> read(byte[] bytes, String sheetName) throws IOException {
        try (Workbook workbook = open(bytes)) {
            Sheet sheet = resolveSheet(workbook, sheetName);
            if (sheet == null) {
                throw new IOException("工作表不存在: " + sheetName);
            }
            FormulaEvaluator evaluator = workbook.getCreationHelper().createFormulaEvaluator();
            // 中文 Excel 常把日期存成「本地化显示」，DataFormatter 会按单元格格式渲染，
            // 比逐个判断 DATE 格式更贴近用户所见
            DataFormatter formatter = new DataFormatter();
            int lastRow = sheet.getLastRowNum();
            int width = 0;
            for (int r = sheet.getFirstRowNum(); r <= lastRow; r++) {
                Row row = sheet.getRow(r);
                if (row != null) {
                    width = Math.max(width, row.getLastCellNum());
                }
            }
            List<List<String>> matrix = new ArrayList<>();
            for (int r = sheet.getFirstRowNum(); r <= lastRow && matrix.size() < MAX_ROWS; r++) {
                Row row = sheet.getRow(r);
                List<String> cells = new ArrayList<>(width);
                for (int c = 0; c < width; c++) {
                    cells.add(cellText(row == null ? null : row.getCell(c), formatter, evaluator));
                }
                matrix.add(cells);
            }
            return matrix;
        }
    }

    /**
     * 取单元格的显示文本。
     *
     * @param cell      单元格，可为 null（整行不存在）
     * @param formatter POI 显示值格式化器
     * @param evaluator 公式求值器
     * @return 文本；null / 空白返回空串
     */
    private static String cellText(Cell cell, DataFormatter formatter, FormulaEvaluator evaluator) {
        if (cell == null) {
            return "";
        }
        CellType type = cell.getCellType();
        if (type == CellType.FORMULA) {
            // 公式取最后一次计算值；取不到就退回公式本身文本，好过丢数据
            try {
                type = evaluator.evaluateFormulaCell(cell);
            } catch (RuntimeException ignored) {
                return formatter.formatCellValue(cell);
            }
        }
        if (type == CellType.NUMERIC) {
            if (DateUtil.isCellDateFormatted(cell)) {
                try {
                    return cell.getLocalDateTimeCellValue()
                            .format(java.time.format.DateTimeFormatter.ofPattern(DATE_TIME_PATTERN));
                } catch (RuntimeException ignored) {
                    return formatter.formatCellValue(cell);
                }
            }
            double raw = cell.getNumericCellValue();
            // stripTrailingZeros 让 1 → "1"、1.5 → "1.5"，而不是 "1.0" / "1.500000"
            BigDecimal decimal = BigDecimal.valueOf(raw).stripTrailingZeros();
            return decimal.scale() < 0 ? decimal.setScale(0).toPlainString() : decimal.toPlainString();
        }
        if (type == CellType.BOOLEAN) {
            return cell.getBooleanCellValue() ? "1" : "0";
        }
        if (type == CellType.BLANK) {
            return "";
        }
        // STRING / ERROR / 其它：交给 DataFormatter，它会正确处理富文本与格式化
        return formatter.formatCellValue(cell);
    }

    /**
     * 按名称取工作表；名称为空时取第一张。
     *
     * @param workbook  工作簿
     * @param sheetName 工作表名
     * @return 工作表；不存在返回 null
     */
    private static Sheet resolveSheet(Workbook workbook, String sheetName) {
        if (sheetName == null || sheetName.isBlank()) {
            return workbook.getNumberOfSheets() == 0 ? null : workbook.getSheetAt(0);
        }
        // 先按严格名匹配，再退回忽略大小写：Excel 工作表名本身区分大小写，
        // 但用户从下拉里复制时容易带上前后空格
        Sheet exact = workbook.getSheet(sheetName);
        if (exact != null) {
            return exact;
        }
        for (int i = 0; i < workbook.getNumberOfSheets(); i++) {
            if (workbook.getSheetName(i).equalsIgnoreCase(sheetName.trim())) {
                return workbook.getSheetAt(i);
            }
        }
        return null;
    }

    /**
     * 打开工作簿。
     *
     * @param bytes 字节
     * @return 工作簿（调用方负责关闭）
     * @throws IOException 格式非法
     */
    private static Workbook open(byte[] bytes) throws IOException {
        if (bytes == null || bytes.length == 0) {
            throw new IOException("Excel 文件内容为空");
        }
        return WorkbookFactory.create(new ByteArrayInputStream(bytes));
    }
}
