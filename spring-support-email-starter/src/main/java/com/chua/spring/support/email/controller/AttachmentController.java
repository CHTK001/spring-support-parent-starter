package com.chua.spring.support.email.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 附件管理控制器
 * 
 * @author CH
 * @since 2026-03-18
 */
@Slf4j
@RestController
@RequestMapping("/api/attachment")
@RequiredArgsConstructor
public class AttachmentController {

    /**
     * 上传附件
     * 
     * @param files 附件文件列表
     * @return 上传结果
     */
    @PostMapping("/upload")
    public ResponseEntity<Map<String, Object>> uploadAttachments(
            @RequestParam("files") MultipartFile[] files) {

        Map<String, Object> response = new HashMap<>();
        List<Map<String, Object>> attachments = new ArrayList<>();

        try {
            for (MultipartFile file : files) {
                if (file.isEmpty()) {
                    continue;
                }

                Map<String, Object> attachment = new HashMap<>();
                attachment.put("name", file.getOriginalFilename());
                attachment.put("size", file.getSize());
                attachment.put("contentType", file.getContentType());

                // 这里应该将文件保存到文件系统或对象存储
                // 暂时只返回文件信息
                String fileId = "file_" + System.currentTimeMillis() + "_" + file.getOriginalFilename();
                attachment.put("id", fileId);

                attachments.add(attachment);

                log.info("上传附件: {}, 大小: {} bytes", file.getOriginalFilename(), file.getSize());
            }

            response.put("success", true);
            response.put("message", "上传成功");
            response.put("data", attachments);

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("上传附件失败", e);
            response.put("success", false);
            response.put("message", "上传失败: " + e.getMessage());
            return ResponseEntity.ok(response);
        }
    }

    /**
     * 获取邮件附件列表
     * 
     * @param emailId 邮件ID
     * @return 附件列表
     */
    @GetMapping("/list/{emailId}")
    public ResponseEntity<Map<String, Object>> getAttachments(@PathVariable String emailId) {
        Map<String, Object> response = new HashMap<>();

        try {
            // 这里应该从数据库或邮件服务器获取附件列表
            // 暂时返回模拟数据
            List<Map<String, Object>> attachments = new ArrayList<>();

            Map<String, Object> attachment1 = new HashMap<>();
            attachment1.put("id", "att_1");
            attachment1.put("name", "document.pdf");
            attachment1.put("size", 1024000L);
            attachment1.put("contentType", "application/pdf");
            attachments.add(attachment1);

            Map<String, Object> attachment2 = new HashMap<>();
            attachment2.put("id", "att_2");
            attachment2.put("name", "image.jpg");
            attachment2.put("size", 512000L);
            attachment2.put("contentType", "image/jpeg");
            attachments.add(attachment2);

            response.put("success", true);
            response.put("data", attachments);

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("获取附件列表失败", e);
            response.put("success", false);
            response.put("message", "获取失败: " + e.getMessage());
            return ResponseEntity.ok(response);
        }
    }

    /**
     * 下载附件
     * 
     * @param emailId      邮件ID
     * @param attachmentId 附件ID
     * @return 附件文件
     */
    @GetMapping("/download/{emailId}/{attachmentId}")
    public ResponseEntity<Resource> downloadAttachment(
            @PathVariable String emailId,
            @PathVariable String attachmentId) {

        try {
            // 这里应该从文件系统或对象存储获取文件
            // 暂时返回模拟数据
            byte[] data = "This is a test file content".getBytes();
            ByteArrayResource resource = new ByteArrayResource(data);

            String filename = "attachment_" + attachmentId + ".txt";

            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .contentLength(data.length)
                    .body(resource);

        } catch (Exception e) {
            log.error("下载附件失败", e);
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * 删除附件
     * 
     * @param attachmentId 附件ID
     * @return 删除结果
     */
    @DeleteMapping("/{attachmentId}")
    public ResponseEntity<Map<String, Object>> deleteAttachment(@PathVariable String attachmentId) {
        Map<String, Object> response = new HashMap<>();

        try {
            // 这里应该从文件系统或对象存储删除文件
            log.info("删除附件: {}", attachmentId);

            response.put("success", true);
            response.put("message", "删除成功");

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("删除附件失败", e);
            response.put("success", false);
            response.put("message", "删除失败: " + e.getMessage());
            return ResponseEntity.ok(response);
        }
    }
}
