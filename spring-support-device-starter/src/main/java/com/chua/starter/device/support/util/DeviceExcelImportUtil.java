// [P3C A20 豁免] A20/A30/A31: 含日志/界面/SQL 字符串字面量等非业务魔法值场景，豁免。
package com.chua.starter.device.support.util;

import jakarta.servlet.http.HttpServletResponse;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.FormulaEvaluator;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 设备台账 Excel 导入 / 模板工具。
 *
 * <p>模板表头与「导出台账」保持同一套列，用户可以直接把导出文件改完再导回来。
 * 读取时按表头文字匹配列（不是按固定列序），所以调整列顺序不会读错。</p>
 *
 * <p>读出的每一行是「规范化字段名 → 文本值」，字段名见 {@link #HEADERS} 对应的
 * {@link #KEYS}；数值解析与业务校验放在导入服务里做，本工具只负责把 Excel 变文本。</p>
 *
 * @since 4.0.0.51
 * @author CH
 */
public final class DeviceExcelImportUtil {

    /**
     * 模板表头（与导出列一致）
     */
    private static final String[] HEADERS = {
            "设备ID", "设备名称", "厂商", "型号", "大类", "小类",
            "IP地址", "端口", "在线状态", "序列号", "固件版本", "通道数",
            "纬度", "经度", "地址", "创建时间"
    };

    /**
     * 表头 → 规范化字段名
     */
    private static final String[] KEYS = {
            "deviceId", "name", "vendor", "model", "category", "subCategory",
            "ip", "port", "online", "serial", "firmware", "channelCount",
            "latitude", "longitude", "address", "createdAt"
    };

    /**
     * 表头别名：允许用户把「设备ID」写成「设备编号 / ID」等常见叫法
     */
    private static final Map<String, String> ALIASES = new LinkedHashMap<>();

    static {
        for (int i = 0; i < HEADERS.length; i++) {
            ALIASES.put(normalize(HEADERS[i]), KEYS[i]);
        }
        ALIASES.put(normalize("设备编号"), "deviceId");
        ALIASES.put(normalize("ID"), "deviceId");
        ALIASES.put(normalize("名称"), "name");
        ALIASES.put(normalize("IP"), "ip");
        ALIASES.put(normalize("序列号/编号"), "serial");
    }

    /**
     * 数据页名
     */
    private static final String SHEET_DATA = "设备列表";

    /**
     * 说明页名
     */
    private static final String SHEET_HELP = "填写说明";

    /**
     * 列宽（字符数）
     */
    private static final int COLUMN_WIDTH = 18;

    /**
     * 工具类不可实例化。
     */
    private DeviceExcelImportUtil() {
    }

    /**
     * 读取第一个 sheet 的数据行。
     *
     * @param in Excel 输入流（xlsx / xls 均可）
     * @return 每行一个「字段名 → 文本」映射；完全空白的行会被跳过
     * @throws IOException 读取失败
     */
    public static List<Map<String, String>> readRows(InputStream in) throws IOException {
        List<Map<String, String>> result = new ArrayList<>();
        try (Workbook workbook = WorkbookFactory.create(in)) {
            if (workbook.getNumberOfSheets() == 0) {
                return result;
            }
            Sheet sheet = workbook.getSheetAt(0);
            Row header = sheet.getRow(sheet.getFirstRowNum());
            if (header == null) {
                return result;
            }
            DataFormatter formatter = new DataFormatter();
            FormulaEvaluator evaluator = workbook.getCreationHelper().createFormulaEvaluator();
            Map<Integer, String> columnKeys = new LinkedHashMap<>();
            for (Cell cell : header) {
                String key = ALIASES.get(normalize(cellText(cell, formatter, evaluator)));
                if (key != null && !columnKeys.containsValue(key)) {
                    columnKeys.put(cell.getColumnIndex(), key);
                }
            }
            if (columnKeys.isEmpty()) {
                return result;
            }
            int headerRow = header.getRowNum();
            for (int index = headerRow + 1; index <= sheet.getLastRowNum(); index++) {
                Row row = sheet.getRow(index);
                if (row == null) {
                    continue;
                }
                Map<String, String> data = new LinkedHashMap<>();
                boolean hasValue = false;
                for (Map.Entry<Integer, String> entry : columnKeys.entrySet()) {
                    String text = cellText(row.getCell(entry.getKey()), formatter, evaluator);
                    data.put(entry.getValue(), text);
                    if (!text.isBlank()) {
                        hasValue = true;
                    }
                }
                if (hasValue) {
                    result.add(data);
                }
            }
        }
        return result;
    }

    /**
     * 写出导入模板（数据页 + 填写说明页）。
     *
     * @param response HTTP 响应
     * @throws IOException 写出失败
     */
    public static void writeTemplate(HttpServletResponse response) throws IOException {
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet(SHEET_DATA);
            Row header = sheet.createRow(0);
            for (int i = 0; i < HEADERS.length; i++) {
                header.createCell(i).setCellValue(HEADERS[i]);
                sheet.setColumnWidth(i, COLUMN_WIDTH * 256);
            }

            Sheet help = workbook.createSheet(SHEET_HELP);
            String[] notes = {
                    "1. 设备名称必填；设备ID 在「更新 / 新增更新」模式下作为匹配键。",
                    "2. 新增模式：设备ID 可留空（服务端生成）；填了已存在的 ID 会报错。",
                    "3. 更新模式：必须填一个已存在的设备ID。",
                    "4. 新增/更新：ID 存在则更新，不存在则新增。",
                    "5. 「在线状态」「创建时间」是导出时带出的只读列，导入会忽略。",
                    "6. 单次最多 5000 行；大类建议 camera / accessControl / alarm / intercom。",
                    "7. 端口 / 通道数 / 纬度 / 经度填数字；留空表示不修改。"
            };
            for (int i = 0; i < notes.length; i++) {
                help.createRow(i).createCell(0).setCellValue(notes[i]);
            }
            help.setColumnWidth(0, 90 * 256);

            String filename = URLEncoder.encode("设备台账导入模板.xlsx", StandardCharsets.UTF_8);
            response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            response.setHeader("Content-Disposition", "attachment; filename=" + filename);
            try (OutputStream out = response.getOutputStream()) {
                workbook.write(out);
            }
        }
    }

    /**
     * 读单元格文本：数字不带多余小数、公式先求值。
     *
     * @param cell      单元格，可空
     * @param formatter 格式化器
     * @param evaluator 公式求值器
     * @return 去首尾空白的文本
     */
    private static String cellText(Cell cell, DataFormatter formatter, FormulaEvaluator evaluator) {
        if (cell == null) {
            return "";
        }
        return formatter.formatCellValue(cell, evaluator).trim();
    }

    /**
     * 表头归一化：去空白、去全角空格、转小写，便于别名匹配。
     *
     * @param text 原始表头
     * @return 归一化文本
     */
    private static String normalize(String text) {
        if (text == null) {
            return "";
        }
        return text.replace("\u3000", "").replace(" ", "").trim().toLowerCase();
    }
}
