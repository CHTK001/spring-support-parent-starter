package com.chua.starter.device.support.util;

import com.chua.starter.device.support.entity.DeviceInfo;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.OutputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
* Excel 导出工具
* <p>支持将设备列表导出为 .xlsx 文件，供前端下载。</p>
*
* @since 4.0.0.42
* @author CH
 */
@Slf4j
public class ExcelExportUtil {

    /**
    * 表头字段数组
    */
    private static final String[] HEADERS = {
        "设备ID", "设备名称", "厂商", "型号", "大类", "小类",
        "IP地址", "端口", "在线状态", "序列号", "固件版本", "通道数",
        "纬度", "经度", "地址", "创建时间"
    };

    /**
    * 导出设备列表为 Excel。
    *
    * @param response HTTP 响应对象
    * @param devices  设备列表
    * @throws Exception 导出异常
    */
    public static void exportDevices(HttpServletResponse response, List<DeviceInfo> devices) throws Exception {
        XSSFWorkbook workbook = new XSSFWorkbook();
        /**
        * 尝试。
        * @param workbook workbook
        */
        try (workbook) {
            Sheet sheet = workbook.createSheet("设备列表");

            // 表头
            Row header = sheet.createRow(0);
            for (int i = 0; i < HEADERS.length; i++) {
                Cell cell = header.createCell(i);
                cell.setCellValue(HEADERS[i]);
            }

            // 数据行
            int rowIndex = 1;
            for (DeviceInfo device : devices) {
                Row row = sheet.createRow(rowIndex++);
                row.createCell(0).setCellValue(safeString(device.getDeviceInfoId()));
                row.createCell(1).setCellValue(safeString(device.getDeviceInfoName()));
                row.createCell(2).setCellValue(safeString(device.getDeviceInfoVendor()));
                row.createCell(3).setCellValue(safeString(device.getDeviceInfoModel()));
                row.createCell(4).setCellValue(safeString(device.getDeviceInfoCategory()));
                row.createCell(5).setCellValue(safeString(device.getDeviceInfoSubCategory()));
                row.createCell(6).setCellValue(safeString(device.getDeviceInfoIp()));
                row.createCell(7).setCellValue(device.getDeviceInfoPort() != null ? device.getDeviceInfoPort() : 0);
                row.createCell(8).setCellValue(Boolean.TRUE.equals(device.getDeviceInfoOnline()) ? "在线" : "离线");
                row.createCell(9).setCellValue(safeString(device.getDeviceInfoSerial()));
                row.createCell(10).setCellValue(safeString(device.getDeviceInfoFirmware()));
                row.createCell(11).setCellValue(device.getDeviceInfoChannelCount() != null ? device.getDeviceInfoChannelCount() : 0);
                row.createCell(12).setCellValue(device.getDeviceInfoLatitude() != null ? device.getDeviceInfoLatitude().toString() : "");
                row.createCell(13).setCellValue(device.getDeviceInfoLongitude() != null ? device.getDeviceInfoLongitude().toString() : "");
                row.createCell(14).setCellValue(safeString(device.getDeviceInfoAddress()));
                row.createCell(15).setCellValue(device.getDeviceInfoCreatedAt() != null ? device.getDeviceInfoCreatedAt().toString() : "");
            }

            // 设置响应头
            String filename = URLEncoder.encode("设备列表.xlsx", StandardCharsets.UTF_8);
            response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            response.setHeader("Content-Disposition", "attachment; filename=" + filename);

            /**
            * 尝试。
            * @param response.getOutputStream( 响应.获取输出流(
            */
            try (OutputStream os = response.getOutputStream()) {
                workbook.write(os);
            }
            log.info("导出设备列表成功: count={}", devices.size());
        }
    }

    /**
    * 安全获取字符串值（防空指针）。
    *
    * @param value 输入值
    * @return 非空字符串
    */
    private static String safeString(String value) {
        return value != null ? value : "";
    }
}
