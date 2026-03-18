package com.chua.spring.support.email.service;

import com.chua.spring.support.email.entity.EmailTemplate;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 邮件模板服务
 * 
 * @author CH
 */
@Slf4j
@Service
public class TemplateService {

    private final Map<String, EmailTemplate> templateStore = new ConcurrentHashMap<>();
    private static final Pattern VARIABLE_PATTERN = Pattern.compile("\\$\\{([^}]+)\\}");

    public TemplateService() {
        // 初始化默认模板
        initDefaultTemplates();
    }

    /**
     * 初始化默认模板
     */
    private void initDefaultTemplates() {
        // 欢迎邮件模板
        EmailTemplate welcome = new EmailTemplate();
        welcome.setId("welcome");
        welcome.setName("欢迎邮件");
        welcome.setDescription("新用户欢迎邮件模板");
        welcome.setSubject("欢迎使用 ${appName}");
        welcome.setContent("您好 ${userName}，\n\n欢迎使用 ${appName}！\n\n祝您使用愉快！\n\n${appName} 团队");
        welcome.setIsHtml(false);
        welcome.setCreatedAt(new Date());
        welcome.setUpdatedAt(new Date());
        templateStore.put(welcome.getId(), welcome);

        // 通知邮件模板
        EmailTemplate notification = new EmailTemplate();
        notification.setId("notification");
        notification.setName("系统通知");
        notification.setDescription("系统通知邮件模板");
        notification.setSubject("${title}");
        notification.setContent("您好 ${userName}，\n\n${message}\n\n此致\n${appName}");
        notification.setIsHtml(false);
        notification.setCreatedAt(new Date());
        notification.setUpdatedAt(new Date());
        templateStore.put(notification.getId(), notification);

        // HTML 邮件模板
        EmailTemplate htmlTemplate = new EmailTemplate();
        htmlTemplate.setId("html-newsletter");
        htmlTemplate.setName("HTML 新闻邮件");
        htmlTemplate.setDescription("HTML 格式的新闻邮件模板");
        htmlTemplate.setSubject("${title}");
        htmlTemplate
                .setContent("<html><body><h1>${title}</h1><p>${content}</p><hr/><p>发送自 ${appName}</p></body></html>");
        htmlTemplate.setIsHtml(true);
        htmlTemplate.setCreatedAt(new Date());
        htmlTemplate.setUpdatedAt(new Date());
        templateStore.put(htmlTemplate.getId(), htmlTemplate);
    }

    /**
     * 保存模板
     */
    public EmailTemplate saveTemplate(EmailTemplate template) {
        if (template.getId() == null || template.getId().isEmpty()) {
            template.setId(UUID.randomUUID().toString());
            template.setCreatedAt(new Date());
        }
        template.setUpdatedAt(new Date());

        templateStore.put(template.getId(), template);
        log.info("保存模板: {}", template.getName());
        return template;
    }

    /**
     * 获取模板列表
     */
    public List<EmailTemplate> getTemplateList() {
        return new ArrayList<>(templateStore.values());
    }

    /**
     * 获取模板详情
     */
    public EmailTemplate getTemplate(String id) {
        return templateStore.get(id);
    }

    /**
     * 删除模板
     */
    public boolean deleteTemplate(String id) {
        EmailTemplate removed = templateStore.remove(id);
        if (removed != null) {
            log.info("删除模板: {}", removed.getName());
            return true;
        }
        return false;
    }

    /**
     * 渲染模板
     */
    public String renderTemplate(String templateId, Map<String, String> variables) {
        EmailTemplate template = getTemplate(templateId);
        if (template == null) {
            throw new RuntimeException("模板不存在: " + templateId);
        }

        return renderContent(template.getContent(), variables);
    }

    /**
     * 渲染模板主题
     */
    public String renderSubject(String templateId, Map<String, String> variables) {
        EmailTemplate template = getTemplate(templateId);
        if (template == null) {
            throw new RuntimeException("模板不存在: " + templateId);
        }

        return renderContent(template.getSubject(), variables);
    }

    /**
     * 渲染内容（替换变量）
     */
    private String renderContent(String content, Map<String, String> variables) {
        if (content == null || variables == null) {
            return content;
        }

        StringBuffer result = new StringBuffer();
        Matcher matcher = VARIABLE_PATTERN.matcher(content);

        while (matcher.find()) {
            String varName = matcher.group(1);
            String varValue = variables.getOrDefault(varName, "${" + varName + "}");
            matcher.appendReplacement(result, Matcher.quoteReplacement(varValue));
        }
        matcher.appendTail(result);

        return result.toString();
    }

    /**
     * 提取模板中的变量
     */
    public Set<String> extractVariables(String content) {
        Set<String> variables = new HashSet<>();
        Matcher matcher = VARIABLE_PATTERN.matcher(content);

        while (matcher.find()) {
            variables.add(matcher.group(1));
        }

        return variables;
    }
}
