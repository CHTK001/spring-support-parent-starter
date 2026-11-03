package com.chua.starter.channel.template;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.chua.starter.ai.support.manage.common.ApiErrorCode;
import com.chua.starter.ai.support.manage.common.ApiException;
import com.chua.starter.channel.template.mapper.SysAiChannelMessageTemplateMapper;
import com.chua.starter.channel.template.store.SysAiChannelMessageTemplate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 短信 / 邮件消息模板服务 —— 模板的增删改查、默认模板选择与变量渲染。
 *
 * <p>职责边界：</p>
 * <ul>
 *   <li>模板元数据：按种类（{@code SMS} / {@code EMAIL}）维护多套模板，同一种类仅一条默认模板；</li>
 *   <li>渲染：把 {@code {{变量}}} 占位替换为实际值，返回缺失变量名便于前端提示；</li>
 *   <li>收信装饰：临时账号轮询到新短信 / 新邮件时，用默认模板给消息补一个 {@code display} 字段，
 *       供渠道管理页的短信 / 邮箱组件直接展示「已渲染」的文本。</li>
 * </ul>
 *
 * <p>默认模板按需读取并缓存 {@value #DEFAULT_CACHE_MILLIS} 毫秒，模板变更立即失效缓存，
 * 避免轮询路径上每个身份都打一次库。</p>
 *
 * @author CH
 * @since 4.0.0.51
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MessageTemplateService {

    /**
     * 短信模板种类
     */
    public static final String KIND_SMS = "SMS";

    /**
     * 邮件模板种类
     */
    public static final String KIND_EMAIL = "EMAIL";

    /**
     * 变量占位正则（支持 {{ name }} 形式的前后空格）
     */
    private static final Pattern VARIABLE_PATTERN = Pattern.compile("\\{\\{\\s*([a-zA-Z0-9_]+)\\s*}}");

    /**
     * 验证码抽取正则（4-8 位连续数字）
     */
    private static final Pattern CODE_PATTERN = Pattern.compile("\\b(\\d{4,8})\\b");

    /**
     * 默认模板缓存时长（毫秒）
     */
    private static final long DEFAULT_CACHE_MILLIS = 30_000L;

    /**
     * HTML 标签清理正则（邮件正文摘要用）
     */
    private static final Pattern HTML_PATTERN = Pattern.compile("<[^>]+>");

    /**
     * 时间格式
     */
    private static final DateTimeFormatter TIME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());

    /**
     * 模板映射器
     */
    private final SysAiChannelMessageTemplateMapper mapper;

    /**
     * 默认模板快照（volatile：模板变更时置空，下次读取重建）
     */
    private volatile DefaultSnapshot snapshot;

    /**
     * 查询模板列表。
     *
     * @param kind 种类（{@code SMS}/{@code EMAIL}，可空表示全部）
     * @return 模板列表：种类升序、默认优先、排序号升序
     */
    public List<SysAiChannelMessageTemplate> list(String kind) {
        LambdaQueryWrapper<SysAiChannelMessageTemplate> wrapper = Wrappers.lambdaQuery();
        if (kind != null && !kind.isBlank()) {
            wrapper.eq(SysAiChannelMessageTemplate::getTemplateKind, normalizeKind(kind));
        }
        wrapper.orderByAsc(SysAiChannelMessageTemplate::getTemplateKind)
                .orderByDesc(SysAiChannelMessageTemplate::getTemplateDefault)
                .orderByAsc(SysAiChannelMessageTemplate::getTemplateSortOrder)
                .orderByDesc(SysAiChannelMessageTemplate::getTemplateUpdatedAt);
        return mapper.selectList(wrapper);
    }

    /**
     * 新增或更新模板。
     *
     * <p>传入 {@code templateId} 视为更新，否则生成新标识插入；同一种类下
     * 编码唯一、默认模板唯一，冲突时抛出业务异常。</p>
     *
     * @param input 模板内容
     * @return 落库后的模板
     */
    @Transactional(rollbackFor = Exception.class)
    public SysAiChannelMessageTemplate save(SysAiChannelMessageTemplate input) {
        if (input == null) {
            throw new ApiException(ApiErrorCode.INVALID_PARAM, "模板内容不能为空");
        }
        String kind = normalizeKind(input.getTemplateKind());
        String name = requireText(input.getTemplateName(), "模板名称");
        String code = requireText(input.getTemplateCode(), "模板编码");
        String content = requireText(input.getTemplateContent(), "模板正文");
        long now = System.currentTimeMillis();

        SysAiChannelMessageTemplate target;
        if (input.getTemplateId() == null || input.getTemplateId().isBlank()) {
            target = new SysAiChannelMessageTemplate();
            target.setTemplateId(UUID.randomUUID().toString().replace("-", ""));
            target.setTemplateCreatedAt(now);
            target.setTemplateKind(kind);
        } else {
            target = mapper.selectById(input.getTemplateId());
            if (target == null) {
                throw new ApiException(ApiErrorCode.NOT_FOUND, "模板不存在: " + input.getTemplateId());
            }
            target.setTemplateKind(kind);
        }

        // 同种类编码唯一性校验（排除自身），DB 唯一索引作为兜底
        long duplicated = mapper.selectCount(Wrappers.<SysAiChannelMessageTemplate>lambdaQuery()
                .eq(SysAiChannelMessageTemplate::getTemplateKind, kind)
                .eq(SysAiChannelMessageTemplate::getTemplateCode, code)
                .ne(target.getTemplateId() != null,
                        SysAiChannelMessageTemplate::getTemplateId, target.getTemplateId()));
        if (duplicated > 0) {
            throw new ApiException(ApiErrorCode.CONFLICT, "同种类下模板编码已存在: " + code);
        }

        target.setTemplateName(name);
        target.setTemplateCode(code);
        target.setTemplateSubject(input.getTemplateSubject());
        target.setTemplateContent(content);
        // 变量清单以正文 / 主题中实际出现的占位为准，避免前端缓存导致的过期清单；
        // 调用方额外声明的变量（如脚本用变量）一并保留。
        List<String> declared = new ArrayList<>(extractVariables(content, input.getTemplateSubject()));
        if (input.getTemplateVariables() != null) {
            for (String variable : input.getTemplateVariables()) {
                if (variable != null && !variable.isBlank() && !declared.contains(variable)) {
                    declared.add(variable);
                }
            }
        }
        target.setTemplateVariables(declared);
        target.setTemplateEnabled(input.getTemplateEnabled() == null || input.getTemplateEnabled());
        target.setTemplateDefault(input.getTemplateDefault() != null && input.getTemplateDefault());
        target.setTemplateSortOrder(input.getTemplateSortOrder() == null ? 0 : input.getTemplateSortOrder());
        target.setTemplateUpdatedAt(now);

        if (target.getTemplateCreatedAt() == null) {
            target.setTemplateCreatedAt(now);
        }
        if (target.getTemplateDefault()) {
            clearDefault(kind, target.getTemplateId());
        }
        if (input.getTemplateId() == null || input.getTemplateId().isBlank()) {
            mapper.insert(target);
        } else {
            mapper.updateById(target);
        }
        invalidateCache();
        return target;
    }

    /**
     * 删除模板。
     *
     * @param templateId 模板标识
     * @return 是否删除成功
     */
    @Transactional(rollbackFor = Exception.class)
    public boolean delete(String templateId) {
        if (templateId == null || templateId.isBlank()) {
            return false;
        }
        int removed = mapper.deleteById(templateId);
        invalidateCache();
        return removed > 0;
    }

    /**
     * 把模板设为该种类的默认模板（同种类其它模板自动取消默认）。
     *
     * @param templateId 模板标识
     * @return 设置后的模板
     */
    @Transactional(rollbackFor = Exception.class)
    public SysAiChannelMessageTemplate setDefault(String templateId) {
        SysAiChannelMessageTemplate target = mapper.selectById(templateId);
        if (target == null) {
            throw new ApiException(ApiErrorCode.NOT_FOUND, "模板不存在: " + templateId);
        }
        clearDefault(target.getTemplateKind(), target.getTemplateId());
        target.setTemplateDefault(true);
        target.setTemplateEnabled(true);
        target.setTemplateUpdatedAt(System.currentTimeMillis());
        mapper.updateById(target);
        invalidateCache();
        return target;
    }

    /**
     * 渲染模板（预览与收信装饰共用）。
     *
     * @param kind      种类
     * @param subject   主题模板（可空）
     * @param content   正文模板
     * @param variables 变量值
     * @return {@code {subject, content, missing}}，{@code missing} 为未提供值的变量名
     */
    public Map<String, Object> render(String kind, String subject, String content,
            Map<String, Object> variables) {
        normalizeKind(kind);
        Map<String, Object> values = new LinkedHashMap<>();
        if (variables != null) {
            values.putAll(variables);
        }
        // 渲染期内置变量：时间相关，避免模板里出现不可解释的空值
        long now = System.currentTimeMillis();
        values.putIfAbsent("time", TIME_FORMATTER.format(Instant.ofEpochMilli(now)));
        values.putIfAbsent("date", java.time.LocalDate.now().toString());
        values.putIfAbsent("timestamp", now);

        Set<String> missing = new LinkedHashSet<>();
        String renderedSubject = substitute(subject, values, missing);
        String renderedContent = substitute(content, values, missing);

        Map<String, Object> result = new LinkedHashMap<>(4);
        result.put("subject", renderedSubject);
        result.put("content", renderedContent);
        result.put("missing", new ArrayList<>(missing));
        return result;
    }

    /**
     * 用默认模板给消息列表补 {@code display} 字段。
     *
     * <p>轮询到新短信 / 新邮件时调用：每个消息的原始 payload 保持不变，
     * 仅追加 {@code display = {templateId, templateName, subject, content}}，
     * 前端短信 / 邮箱组件优先展示 {@code display}，没有默认模板时退回原文。</p>
     *
     * @param kind      种类
     * @param messages  消息 payload 列表
     * @param extraVars 额外变量（如所属临时身份的 {@code phone} / {@code email}）
     */
    public void decorate(String kind, List<Map<String, Object>> messages, Map<String, Object> extraVars) {
        if (messages == null || messages.isEmpty()) {
            return;
        }
        SysAiChannelMessageTemplate template;
        try {
            template = defaultTemplate(kind);
        } catch (Exception e) {
            // 模板表未初始化 / 查询失败时，收信主流程不受影响（消息退回原文展示）
            log.debug("[channel-template] 默认模板读取失败, kind={}", kind, e);
            return;
        }
        if (template == null) {
            return;
        }
        for (Map<String, Object> message : messages) {
            if (message == null) {
                continue;
            }
            try {
                Map<String, Object> variables = new LinkedHashMap<>();
                if (extraVars != null) {
                    variables.putAll(extraVars);
                }
                variables.putAll(extractVariables(kind, message));
                Map<String, Object> rendered = render(kind,
                        template.getTemplateSubject(), template.getTemplateContent(), variables);
                Map<String, Object> display = new LinkedHashMap<>(4);
                display.put("templateId", template.getTemplateId());
                display.put("templateName", template.getTemplateName());
                display.put("subject", rendered.get("subject"));
                display.put("content", rendered.get("content"));
                message.put("display", display);
            } catch (Exception e) {
                // 单条渲染失败不影响收信主流程
                log.debug("[channel-template] 消息渲染失败, kind={}, template={}", kind,
                        template.getTemplateCode(), e);
            }
        }
    }

    /**
     * 模板种类的内置变量清单（前端展示与插入占位）。
     *
     * @param kind 种类
     * @return 变量名列表
     */
    public List<String> variablesOf(String kind) {
        if (KIND_EMAIL.equals(normalizeKind(kind))) {
            return List.of("from", "subject", "body", "code", "email", "time", "date");
        }
        return List.of("from", "content", "code", "phone", "time", "date");
    }

    /**
     * 取某种类的默认模板（优先 {@code default=1}，否则取第一个启用项）。
     *
     * @param kind 种类
     * @return 默认模板；没有可用模板时返回 {@code null}
     */
    public SysAiChannelMessageTemplate defaultTemplate(String kind) {
        String normalized = normalizeKind(kind);
        long now = System.currentTimeMillis();
        DefaultSnapshot current = snapshot;
        if (current == null || now - current.at() > DEFAULT_CACHE_MILLIS) {
            current = refreshSnapshot();
        }
        return current.byKind().get(normalized);
    }

    /**
     * 失效默认模板缓存（模板写操作后调用）。
     */
    public void invalidateCache() {
        snapshot = null;
    }

    /**
     * 重建默认模板快照。
     *
     * @return 新快照
     */
    private DefaultSnapshot refreshSnapshot() {
        List<SysAiChannelMessageTemplate> enabled = mapper.selectList(
                Wrappers.<SysAiChannelMessageTemplate>lambdaQuery()
                        .eq(SysAiChannelMessageTemplate::getTemplateEnabled, true)
                        .orderByDesc(SysAiChannelMessageTemplate::getTemplateDefault)
                        .orderByAsc(SysAiChannelMessageTemplate::getTemplateSortOrder)
                        .orderByDesc(SysAiChannelMessageTemplate::getTemplateUpdatedAt));
        Map<String, SysAiChannelMessageTemplate> byKind = new LinkedHashMap<>(4);
        for (SysAiChannelMessageTemplate item : enabled) {
            if (item == null || item.getTemplateKind() == null) {
                continue;
            }
            byKind.putIfAbsent(item.getTemplateKind(), item);
        }
        DefaultSnapshot fresh = new DefaultSnapshot(System.currentTimeMillis(), byKind);
        snapshot = fresh;
        return fresh;
    }

    /**
     * 取消同种类其它模板的默认标记。
     *
     * @param kind       种类
     * @param keepId     保留的模板标识（可为空）
     */
    private void clearDefault(String kind, String keepId) {
        var update = Wrappers.<SysAiChannelMessageTemplate>lambdaUpdate()
                .eq(SysAiChannelMessageTemplate::getTemplateKind, kind)
                .set(SysAiChannelMessageTemplate::getTemplateDefault, false);
        if (keepId != null && !keepId.isBlank()) {
            update.ne(SysAiChannelMessageTemplate::getTemplateId, keepId);
        }
        mapper.update(null, update);
    }

    /**
     * 从消息 payload 抽取渲染变量。
     *
     * @param kind    种类
     * @param message 消息
     * @return 变量表
     */
    private Map<String, Object> extractVariables(String kind, Map<String, Object> message) {
        Map<String, Object> variables = new LinkedHashMap<>(8);
        String body;
        if (KIND_EMAIL.equals(normalizeKind(kind))) {
            body = text(message.get("body"));
            if (body.isBlank()) {
                body = stripHtml(text(message.get("html")));
            }
            variables.put("from", text(message.get("from")));
            variables.put("subject", text(message.get("subject")));
            variables.put("body", body);
        } else {
            body = text(message.get("content"));
            variables.put("from", text(message.get("from")));
            variables.put("content", body);
        }
        variables.put("code", extractCode(body));
        variables.put("time", TIME_FORMATTER.format(Instant.ofEpochMilli(timestamp(message))));
        return variables;
    }

    /**
     * 替换 {@code {{变量}}} 占位，未提供的变量保留占位并记录到 missing。
     *
     * @param template 模板文本
     * @param values   变量值
     * @param missing  缺失变量收集器
     * @return 渲染结果
     */
    private static String substitute(String template, Map<String, Object> values, Set<String> missing) {
        if (template == null || template.isEmpty()) {
            return template;
        }
        Matcher matcher = VARIABLE_PATTERN.matcher(template);
        StringBuilder builder = new StringBuilder(template.length());
        while (matcher.find()) {
            String name = matcher.group(1);
            Object value = values.get(name);
            if (value == null || value.toString().isBlank()) {
                missing.add(name);
                matcher.appendReplacement(builder, matcher.group());
            } else {
                matcher.appendReplacement(builder, Matcher.quoteReplacement(value.toString()));
            }
        }
        matcher.appendTail(builder);
        return builder.toString();
    }

    /**
     * 抽取模板中出现的变量名。
     *
     * @param content 正文
     * @param subject 主题
     * @return 去重后的变量名
     */
    private static List<String> extractVariables(String content, String subject) {
        Set<String> names = new LinkedHashSet<>();
        collectVariables(subject, names);
        collectVariables(content, names);
        return new ArrayList<>(names);
    }

    /**
     * 收集单个文本中的变量名。
     *
     * @param text  文本
     * @param names 收集器
     */
    private static void collectVariables(String text, Set<String> names) {
        if (text == null || text.isEmpty()) {
            return;
        }
        Matcher matcher = VARIABLE_PATTERN.matcher(text);
        while (matcher.find()) {
            names.add(matcher.group(1));
        }
    }

    /**
     * 从文本抽取验证码（首个 4-8 位数字串）。
     *
     * @param text 文本
     * @return 验证码；未命中返回空串
     */
    private static String extractCode(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        Matcher matcher = CODE_PATTERN.matcher(text);
        return matcher.find() ? matcher.group(1) : "";
    }

    /**
     * 取消息时间戳（毫秒）。
     *
     * @param message 消息
     * @return 时间戳；缺失时返回当前时间
     */
    private static long timestamp(Map<String, Object> message) {
        Object value = message.get("createdAt");
        if (value == null) {
            value = message.get("receivedAt");
        }
        if (value instanceof Number number) {
            long raw = number.longValue();
            // 兼容秒级时间戳
            return raw < 1_000_000_000_000L ? raw * 1000L : raw;
        }
        if (value != null) {
            try {
                return Long.parseLong(value.toString().trim());
            } catch (NumberFormatException ignored) {
                // 非法时间戳退回当前时间
            }
        }
        return System.currentTimeMillis();
    }

    /**
     * 剥离 HTML 标签。
     *
     * @param html HTML 文本
     * @return 纯文本
     */
    private static String stripHtml(String html) {
        if (html == null || html.isBlank()) {
            return "";
        }
        return HTML_PATTERN.matcher(html).replaceAll(" ").replaceAll("\\s+", " ").trim();
    }

    /**
     * 读取非空文本字段。
     *
     * @param value 值
     * @param label 字段名（用于错误消息）
     * @return 去空格后的值
     */
    private static String requireText(String value, String label) {
        if (value == null || value.isBlank()) {
            throw new ApiException(ApiErrorCode.INVALID_PARAM, label + "不能为空");
        }
        return value.trim();
    }

    /**
     * 归一化模板种类。
     *
     * @param kind 原始种类
     * @return {@code SMS} / {@code EMAIL}
     */
    private static String normalizeKind(String kind) {
        String normalized = kind == null ? "" : kind.trim().toUpperCase();
        if (KIND_SMS.equals(normalized) || KIND_EMAIL.equals(normalized)) {
            return normalized;
        }
        throw new ApiException(ApiErrorCode.INVALID_PARAM, "不支持的模板种类: " + kind);
    }

    /**
     * 安全取文本。
     *
     * @param value 值
     * @return 空串或文本
     */
    private static String text(Object value) {
        return value == null ? "" : value.toString();
    }

    /**
     * 默认模板快照。
     *
     * @param at     生成时间（毫秒）
     * @param byKind 种类 → 默认模板
     */
    private record DefaultSnapshot(long at, Map<String, SysAiChannelMessageTemplate> byKind) {
    }
}
