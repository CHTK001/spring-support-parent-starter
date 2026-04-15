package com.chua.starter.spider.support.security;

import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 运行时日志脱敏工具。
 *
 * <p>将日志字符串或 JSON 字符串中凭证相关字段的值替换为 {@code [REDACTED]}。</p>
 *
 * @author CH
 */
public class CredentialRedactor {

    public static final String REDACTED = "[REDACTED]";

    /** 需要脱敏的字段名关键词（忽略大小写） */
    private static final Set<String> SENSITIVE_KEYS = Set.of(
            "password", "passwd", "pwd", "secret", "apikey", "api_key",
            "accesskey", "access_key", "privatekey", "private_key",
            "token", "credential", "auth", "authorization"
    );

    /**
     * 对 JSON 字符串中的敏感字段值进行脱敏。
     *
     * <p>例如：{@code {"password":"abc123"}} → {@code {"password":"[REDACTED]"}}</p>
     *
     * @param json 原始 JSON 字符串
     * @return 脱敏后的 JSON 字符串
     */
    public String redactJson(String json) {
        if (json == null || json.isBlank()) {
            return json;
        }
        String result = json;
        for (String key : SENSITIVE_KEYS) {
            // 匹配 "key" : "value" 或 "key":"value"
            Pattern pattern = Pattern.compile(
                    "(?i)(\"" + Pattern.quote(key) + "\"\\s*:\\s*)\"([^\"]*?)\"",
                    Pattern.CASE_INSENSITIVE
            );
            Matcher matcher = pattern.matcher(result);
            result = matcher.replaceAll("$1\"" + REDACTED + "\"");
        }
        return result;
    }

    /**
     * 对普通日志字符串中的敏感字段进行脱敏。
     *
     * <p>例如：{@code password=abc123} → {@code password=[REDACTED]}</p>
     *
     * @param logLine 原始日志行
     * @return 脱敏后的日志行
     */
    public String redactLogLine(String logLine) {
        if (logLine == null || logLine.isBlank()) {
            return logLine;
        }
        String result = logLine;
        for (String key : SENSITIVE_KEYS) {
            // 匹配 key=value 或 key: value（value 到空格/逗号/换行结束）
            Pattern pattern = Pattern.compile(
                    "(?i)(" + Pattern.quote(key) + "\\s*[=:]\\s*)([^\\s,;\"'\\]\\}]+)",
                    Pattern.CASE_INSENSITIVE
            );
            Matcher matcher = pattern.matcher(result);
            result = matcher.replaceAll("$1" + REDACTED);
        }
        return result;
    }

    /**
     * 判断字段名是否为敏感字段。
     */
    public boolean isSensitiveField(String fieldName) {
        if (fieldName == null) {
            return false;
        }
        String lower = fieldName.toLowerCase();
        return SENSITIVE_KEYS.stream().anyMatch(lower::contains);
    }
}
