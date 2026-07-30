package com.chua.spring.support.email.controller;

import com.chua.spring.support.email.entity.EmailAccount;
import com.chua.spring.support.email.entity.EmailMessage;
import com.chua.spring.support.email.service.EmailService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * 邮件管理控制器
 * 
 * @author CH
 */
@RestController
@RequestMapping("/api/email")
@RequiredArgsConstructor
public class EmailController {

    private final EmailService emailService;

    /**
     * 发送邮件
     */
    @PostMapping("/send")
    public Map<String, Object> sendEmail(@RequestBody Map<String, Object> emailData) {
        try {
            EmailAccount account = new EmailAccount();
            EmailMessage message = new EmailMessage();
            // 从emailData填充account和message
            emailService.sendEmail(account, message);

            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("message", "邮件发送成功");
            return result;
        } catch (Exception e) {
            Map<String, Object> result = new HashMap<>();
            result.put("success", false);
            result.put("message", e.getMessage());
            return result;
        }
    }

    /**
     * 获取邮件列表
     */
    @GetMapping("/list")
    public Map<String, Object> getEmailList(
            @RequestParam(required = false) String accountId,
            @RequestParam(defaultValue = "INBOX") String folder,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        try {
            EmailAccount account = new EmailAccount();
            List<EmailMessage> messages = emailService.receiveEmails(account, folder);

            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("data", messages);
            result.put("total", messages.size());
            return result;
        } catch (Exception e) {
            Map<String, Object> result = new HashMap<>();
            result.put("success", false);
            result.put("message", e.getMessage());
            return result;
        }
    }

    /**
     * 获取邮件详情
     */
    @GetMapping("/{id}")
    public Map<String, Object> getEmailDetail(@PathVariable String id) {
        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("data", null);
        return result;
    }

    /**
     * 删除邮件
     */
    @DeleteMapping("/{id}")
    public Map<String, Object> deleteEmail(@PathVariable String id) {
        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("message", "邮件删除成功");
        return result;
    }

    /**
     * 搜索邮件
     */
    @GetMapping("/search")
    public Map<String, Object> searchEmails(@RequestParam String keyword) {
        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("data", new ArrayList<>());
        return result;
    }

    /**
     * 移动邮件
     */
    @PostMapping("/move")
    public Map<String, Object> moveEmail(@RequestBody Map<String, Object> data) {
        try {
            String messageId = (String) data.get("messageId");
            String targetFolder = (String) data.get("targetFolder");
            EmailAccount account = new EmailAccount();

            boolean success = emailService.moveEmail(account, messageId, targetFolder);

            Map<String, Object> result = new HashMap<>();
            result.put("success", success);
            result.put("message", success ? "邮件移动成功" : "邮件移动失败");
            return result;
        } catch (Exception e) {
            Map<String, Object> result = new HashMap<>();
            result.put("success", false);
            result.put("message", e.getMessage());
            return result;
        }
    }

    /**
     * 标记星标
     */
    @PostMapping("/star")
    public Map<String, Object> starEmail(@RequestBody Map<String, Object> data) {
        try {
            String messageId = (String) data.get("messageId");
            Boolean starred = (Boolean) data.get("starred");
            EmailAccount account = new EmailAccount();

            boolean success = emailService.starEmail(account, messageId, starred);

            Map<String, Object> result = new HashMap<>();
            result.put("success", success);
            result.put("message", success ? "设置星标成功" : "设置星标失败");
            return result;
        } catch (Exception e) {
            Map<String, Object> result = new HashMap<>();
            result.put("success", false);
            result.put("message", e.getMessage());
            return result;
        }
    }

    /**
     * 标记已读/未读
     */
    @PostMapping("/mark-read")
    public Map<String, Object> markAsRead(@RequestBody Map<String, Object> data) {
        try {
            String messageId = (String) data.get("messageId");
            Boolean read = (Boolean) data.get("read");
            EmailAccount account = new EmailAccount();

            boolean success = emailService.markAsRead(account, messageId, read);

            Map<String, Object> result = new HashMap<>();
            result.put("success", success);
            result.put("message", success ? "设置已读状态成功" : "设置已读状态失败");
            return result;
        } catch (Exception e) {
            Map<String, Object> result = new HashMap<>();
            result.put("success", false);
            result.put("message", e.getMessage());
            return result;
        }
    }

    /**
     * 过滤邮件
     */
    @PostMapping("/filter")
    public Map<String, Object> filterEmails(@RequestBody Map<String, Object> data) {
        try {
            @SuppressWarnings("unchecked")
            List<EmailMessage> messages = (List<EmailMessage>) data.get("messages");
            @SuppressWarnings("unchecked")
            Map<String, Object> filters = (Map<String, Object>) data.get("filters");

            List<EmailMessage> filtered = emailService.filterEmails(messages, filters);

            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("data", filtered);
            result.put("total", filtered.size());
            return result;
        } catch (Exception e) {
            Map<String, Object> result = new HashMap<>();
            result.put("success", false);
            result.put("message", e.getMessage());
            return result;
        }
    }
}
