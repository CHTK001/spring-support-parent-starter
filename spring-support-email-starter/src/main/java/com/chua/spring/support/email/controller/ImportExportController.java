package com.chua.spring.support.email.controller;

import com.chua.spring.support.email.entity.EmailAccount;
import com.chua.spring.support.email.service.EmailAccountImportExportService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.util.*;

/**
 * 导入导出控制器
 * 
 * @author CH
 */
@RestController
@RequestMapping("/api/import-export")
@RequiredArgsConstructor
public class ImportExportController {

    private final EmailAccountImportExportService importExportService;

    /**
     * 导出账户到 JSON
     */
    @PostMapping("/export/json")
    public ResponseEntity<byte[]> exportToJson(@RequestBody List<EmailAccount> accounts) {
        try {
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            importExportService.exportToJson(accounts, outputStream);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setContentDispositionFormData("attachment", "email-accounts.json");

            return ResponseEntity.ok()
                    .headers(headers)
                    .body(outputStream.toByteArray());
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * 导出账户到 CSV
     */
    @PostMapping("/export/csv")
    public ResponseEntity<byte[]> exportToCsv(@RequestBody List<EmailAccount> accounts) {
        try {
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            importExportService.exportToCsv(accounts, outputStream);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.parseMediaType("text/csv"));
            headers.setContentDispositionFormData("attachment", "email-accounts.csv");

            return ResponseEntity.ok()
                    .headers(headers)
                    .body(outputStream.toByteArray());
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * 从 JSON 导入账户
     */
    @PostMapping("/import/json")
    public Map<String, Object> importFromJson(@RequestParam("file") MultipartFile file) {
        try {
            List<EmailAccount> accounts = importExportService.importFromJson(file.getInputStream());

            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("message", "成功导入 " + accounts.size() + " 个账户");
            result.put("data", accounts);
            return result;
        } catch (Exception e) {
            Map<String, Object> result = new HashMap<>();
            result.put("success", false);
            result.put("message", "导入失败: " + e.getMessage());
            return result;
        }
    }

    /**
     * 从 CSV 导入账户
     */
    @PostMapping("/import/csv")
    public Map<String, Object> importFromCsv(@RequestParam("file") MultipartFile file) {
        try {
            List<EmailAccount> accounts = importExportService.importFromCsv(file.getInputStream());

            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("message", "成功导入 " + accounts.size() + " 个账户");
            result.put("data", accounts);
            return result;
        } catch (Exception e) {
            Map<String, Object> result = new HashMap<>();
            result.put("success", false);
            result.put("message", "导入失败: " + e.getMessage());
            return result;
        }
    }
}
