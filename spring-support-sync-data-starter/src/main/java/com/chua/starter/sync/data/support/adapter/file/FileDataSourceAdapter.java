package com.chua.starter.sync.data.support.adapter.file;

import com.chua.starter.sync.data.support.adapter.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Stream;

/**
 * 文件数据源适配器
 */
@Slf4j
public class FileDataSourceAdapter implements DataSourceAdapter {
    
    private DataSourceConfig config;
    private final ObjectMapper objectMapper = new ObjectMapper();
    
    @Override
    public void connect(DataSourceConfig config) throws DataSourceException {
        this.config = config;
        log.info("文件数据源初始化: {}", config.getFilePath());
    }
    
    @Override
    public Stream<Map<String, Object>> read(ReadConfig readConfig) {
        String filePath = readConfig.getFilePath();
        String fileType = readConfig.getFileType();
        
        return switch (fileType.toUpperCase()) {
            case "CSV" -> readCsv(filePath);
            case "EXCEL" -> readExcel(filePath);
            case "JSON" -> readJson(filePath);
            default -> throw new DataSourceException("不支持的文件类型: " + fileType);
        };
    }
    
    private Stream<Map<String, Object>> readCsv(String filePath) {
        try {
            List<String> lines = Files.readAllLines(Paths.get(filePath));
            if (lines.isEmpty()) return Stream.empty();
            
            String[] headers = lines.get(0).split(",");
            return lines.stream().skip(1).map(line -> {
                String[] values = line.split(",");
                Map<String, Object> row = new HashMap<>();
                for (int i = 0; i < headers.length && i < values.length; i++) {
                    row.put(headers[i].trim(), values[i].trim());
                }
                return row;
            });
        } catch (IOException e) {
            throw new DataSourceException("读取CSV文件失败", e);
        }
    }
    
    private Stream<Map<String, Object>> readExcel(String filePath) {
        try (FileInputStream fis = new FileInputStream(filePath);
             Workbook workbook = new XSSFWorkbook(fis)) {
            
            Sheet sheet = workbook.getSheetAt(0);
            Row headerRow = sheet.getRow(0);
            List<String> headers = new ArrayList<>();
            for (Cell cell : headerRow) {
                headers.add(cell.getStringCellValue());
            }
            
            List<Map<String, Object>> rows = new ArrayList<>();
            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null) continue;
                
                Map<String, Object> rowData = new HashMap<>();
                for (int j = 0; j < headers.size(); j++) {
                    Cell cell = row.getCell(j);
                    rowData.put(headers.get(j), getCellValue(cell));
                }
                rows.add(rowData);
            }
            return rows.stream();
        } catch (IOException e) {
            throw new DataSourceException("读取Excel文件失败", e);
        }
    }
    
    private Object getCellValue(Cell cell) {
        if (cell == null) return null;
        return switch (cell.getCellType()) {
            case STRING -> cell.getStringCellValue();
            case NUMERIC -> cell.getNumericCellValue();
            case BOOLEAN -> cell.getBooleanCellValue();
            default -> null;
        };
    }
    
    private Stream<Map<String, Object>> readJson(String filePath) {
        try {
            List<Map> list = objectMapper.readValue(new File(filePath), List.class);
            return list.stream().map(m -> (Map<String, Object>) m);
        } catch (IOException e) {
            throw new DataSourceException("读取JSON文件失败", e);
        }
    }
    
    @Override
    public void write(List<Map<String, Object>> records, WriteConfig writeConfig) {
        if (records == null || records.isEmpty()) return;
        
        String filePath = writeConfig.getFilePath();
        String fileType = writeConfig.getFileType();
        
        switch (fileType.toUpperCase()) {
            case "CSV" -> writeCsv(filePath, records);
            case "EXCEL" -> writeExcel(filePath, records);
            case "JSON" -> writeJson(filePath, records);
            default -> throw new DataSourceException("不支持的文件类型: " + fileType);
        }
    }
    
    private void writeCsv(String filePath, List<Map<String, Object>> records) {
        try (BufferedWriter writer = Files.newBufferedWriter(Paths.get(filePath))) {
            Set<String> headers = records.get(0).keySet();
            writer.write(String.join(",", headers));
            writer.newLine();
            
            for (Map<String, Object> record : records) {
                List<String> values = new ArrayList<>();
                for (String header : headers) {
                    values.add(String.valueOf(record.get(header)));
                }
                writer.write(String.join(",", values));
                writer.newLine();
            }
        } catch (IOException e) {
            throw new DataSourceException("写入CSV文件失败", e);
        }
    }
    
    private void writeExcel(String filePath, List<Map<String, Object>> records) {
        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("Data");
            
            Set<String> headers = records.get(0).keySet();
            Row headerRow = sheet.createRow(0);
            int colIdx = 0;
            for (String header : headers) {
                headerRow.createCell(colIdx++).setCellValue(header);
            }
            
            int rowIdx = 1;
            for (Map<String, Object> record : records) {
                Row row = sheet.createRow(rowIdx++);
                colIdx = 0;
                for (String header : headers) {
                    Object value = record.get(header);
                    if (value != null) {
                        row.createCell(colIdx).setCellValue(value.toString());
                    }
                    colIdx++;
                }
            }
            
            try (FileOutputStream fos = new FileOutputStream(filePath)) {
                workbook.write(fos);
            }
        } catch (IOException e) {
            throw new DataSourceException("写入Excel文件失败", e);
        }
    }
    
    private void writeJson(String filePath, List<Map<String, Object>> records) {
        try {
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(new File(filePath), records);
        } catch (IOException e) {
            throw new DataSourceException("写入JSON文件失败", e);
        }
    }
    
    @Override
    public boolean testConnection() {
        return config != null && config.getFilePath() != null;
    }
    
    @Override
    public void close() {
        log.info("文件数据源已关闭");
    }
    
    @Override
    public DataSourceMetadata getMetadata() {
        DataSourceMetadata metadata = new DataSourceMetadata();
        metadata.setDatabaseType("File");
        return metadata;
    }
}
