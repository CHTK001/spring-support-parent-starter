package com.chua.spring.support.email.controller;

import com.chua.spring.support.email.entity.EmailTemplate;
import com.chua.spring.support.email.service.TemplateService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 邮件模板控制器
 * 
 * @author CH
 */
@Slf4j
@RestController
@RequestMapping("/api/template")
@RequiredArgsConstructor
public class TemplateController {

    private final TemplateService templateService;

    /**
     * 保存模板
     */
    @PostMapping("/save")
    public Map<String, Object> saveTemplate(@RequestBody EmailTemplate template) {
        Map<String, Object> result = new HashMap<>();
        try {
            EmailTemplate saved = templateService.saveTemplate(template);
            result.put("success", true);
            result.put("data", saved);
            result.put("message", "模板保存成功");
        } catch (Exception e) {
            log.error("保存模板失败", e);
            result.put("success", false);
            result.put("message", "保存失败: " + e.getMessage());
        }
        return result;
    }

    /**
     * 获取模板列表
     */
    @GetMapping("/list")
    public Map<String, Object> getTemplateList() {
        Map<String, Object> result = new HashMap<>();
        try {
            List<EmailTemplate> templates = templateService.getTemplateList();
            result.put("success", true);
            result.put("data", templates);
        } catch (Exception e) {
            log.error("获取模板列表失败", e);
            result.put("success", false);
            result.put("message", "获取失败: " + e.getMessage());
        }
        return result;
    }

    /**
     * 获取模板详情
     */
    @GetMapping("/{id}")
    public Map<String, Object> getTemplate(@PathVariable String id) {
        Map<String, Object> result = new HashMap<>();
        try {
            EmailTemplate template = templateService.getTemplate(id);
            if (template != null) {
                result.put("success", true);
                result.put("data", template);
            } else {
                result.put("success", false);
                result.put("message", "模板不存在");
            }
        } catch (Exception e) {
            log.error("获取模板失败", e);
            result.put("success", false);
            result.put("message", "获取失败: " + e.getMessage());
        }
        return result;
    }

    /**
     * 删除模板
     */
    @DeleteMapping("/{id}")
    public Map<String, Object> deleteTemplate(@PathVariable String id) {
        Map<String, Object> result = new HashMap<>();
        try {
            boolean deleted = templateService.deleteTemplate(id);
            result.put("success", deleted);
            result.put("message", deleted ? "删除成功" : "模板不存在");
        } catch (Exception e) {
            log.error("删除模板失败", e);
            result.put("success", false);
            result.put("message", "删除失败: " + e.getMessage());
        }
        return result;
    }

    /**
     * 渲染模板
     */
    @PostMapping("/render")
    public Map<String, Object> renderTemplate(@RequestBody Map<String, Object> data) {
        Map<String, Object> result = new HashMap<>();
        try {
            String templateId = (String) data.get("templateId");
            @SuppressWarnings("unchecked")
            Map<String, String> variables = (Map<String, String>) data.get("variables");

            String subject = templateService.renderSubject(templateId, variables);
            String content = templateService.renderTemplate(templateId, variables);

            result.put("success", true);
            result.put("data", Map.of("subject", subject, "content", content));
        } catch (Exception e) {
            log.error("渲染模板失败", e);
            result.put("success", false);
            result.put("message", "渲染失败: " + e.getMessage());
        }
        return result;
    }

    /**
     * 提取模板变量
     */
    @PostMapping("/extract-variables")
    public Map<String, Object> extractVariables(@RequestBody Map<String, String> data) {
        Map<String, Object> result = new HashMap<>();
        try {
            String content = data.get("content");
            Set<String> variables = templateService.extractVariables(content);

            result.put("success", true);
            result.put("data", variables);
        } catch (Exception e) {
            log.error("提取变量失败", e);
            result.put("success", false);
            result.put("message", "提取失败: " + e.getMessage());
        }
        return result;
    }
}
