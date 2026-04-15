package com.chua.starter.spider.support.security;

import com.chua.starter.spider.support.domain.SpiderFlowNode;
import com.chua.starter.spider.support.domain.SpiderTaskDefinition;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 明文密码检测器。
 *
 * <p>扫描任务配置中疑似明文密码的字段值，检测到则返回安全警告列表。
 * 调用方应在检测结果非空时拒绝保存。</p>
 *
 * <p>检测策略：
 * <ul>
 *   <li>字段名包含敏感关键词（password、passwd、secret、token、apikey 等）</li>
 *   <li>字段值符合常见密码特征（长度 ≥ 6、含字母和数字）</li>
 * </ul>
 * </p>
 *
 * @author CH
 */
public class CredentialSafetyChecker {

    /** 敏感字段名关键词（忽略大小写） */
    private static final Set<String> SENSITIVE_KEY_PATTERNS = Set.of(
            "password", "passwd", "pwd", "secret", "apikey", "api_key",
            "accesskey", "access_key", "privatekey", "private_key",
            "token", "credential", "auth"
    );

    /** 疑似明文密码的值特征：长度 6~128，含字母和数字 */
    private static final Pattern PLAINTEXT_PATTERN =
            Pattern.compile("^(?=.*[a-zA-Z])(?=.*\\d).{6,128}$");

    /**
     * 检查任务定义中是否包含疑似明文密码。
     *
     * @param task 任务定义
     * @return 安全警告列表，空列表表示安全
     */
    public List<String> check(SpiderTaskDefinition task) {
        List<String> warnings = new ArrayList<>();
        if (task == null) {
            return warnings;
        }
        // 检查 executionPolicy JSON 字符串中的敏感字段
        checkJsonString(task.getExecutionPolicy(), "executionPolicy", warnings);
        checkJsonString(task.getAiProfile(), "aiProfile", warnings);
        // credentialRef 本身不存密码，但若误存了明文也要检测
        checkJsonString(task.getCredentialRef(), "credentialRef", warnings);
        return warnings;
    }

    /**
     * 检查节点配置 Map 中是否包含疑似明文密码。
     *
     * @param node 流程节点
     * @return 安全警告列表
     */
    public List<String> checkNode(SpiderFlowNode node) {
        List<String> warnings = new ArrayList<>();
        if (node == null || node.getConfig() == null) {
            return warnings;
        }
        checkMap(node.getConfig(), "nodes[" + node.getNodeId() + "].config", warnings);
        return warnings;
    }

    /**
     * 检查任意 Map 中是否包含疑似明文密码字段。
     */
    public List<String> checkMap(Map<String, Object> config, String contextPath) {
        List<String> warnings = new ArrayList<>();
        checkMap(config, contextPath, warnings);
        return warnings;
    }

    private void checkMap(Map<String, Object> map, String path, List<String> warnings) {
        if (map == null) {
            return;
        }
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();
            String fieldPath = path + "." + key;

            if (isSensitiveKey(key) && value instanceof String strVal && looksLikePlaintext(strVal)) {
                warnings.add("安全警告：字段 [" + fieldPath + "] 疑似包含明文密码，请改用凭证引用");
            }
            // 递归检查嵌套 Map
            if (value instanceof Map<?, ?> nested) {
                @SuppressWarnings("unchecked")
                Map<String, Object> nestedMap = (Map<String, Object>) nested;
                checkMap(nestedMap, fieldPath, warnings);
            }
        }
    }

    private void checkJsonString(String json, String fieldName, List<String> warnings) {
        if (json == null || json.isBlank()) {
            return;
        }
        // 简单扫描：检查 JSON 字符串中是否有敏感 key
        for (String sensitiveKey : SENSITIVE_KEY_PATTERNS) {
            if (json.toLowerCase().contains("\"" + sensitiveKey + "\"")) {
                // 尝试提取对应值做进一步判断
                String lowerJson = json.toLowerCase();
                int keyIdx = lowerJson.indexOf("\"" + sensitiveKey + "\"");
                if (keyIdx >= 0) {
                    int colonIdx = json.indexOf(':', keyIdx);
                    if (colonIdx > 0 && colonIdx < json.length() - 2) {
                        String rest = json.substring(colonIdx + 1).trim();
                        if (rest.startsWith("\"")) {
                            int endQuote = rest.indexOf('"', 1);
                            if (endQuote > 1) {
                                String val = rest.substring(1, endQuote);
                                if (looksLikePlaintext(val)) {
                                    warnings.add("安全警告：字段 [" + fieldName + "." + sensitiveKey
                                            + "] 疑似包含明文密码，请改用凭证引用");
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private boolean isSensitiveKey(String key) {
        if (key == null) {
            return false;
        }
        String lower = key.toLowerCase();
        return SENSITIVE_KEY_PATTERNS.stream().anyMatch(lower::contains);
    }

    private boolean looksLikePlaintext(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        // 排除明显的占位符和引用格式
        if (value.startsWith("${") || value.startsWith("#{") || value.startsWith("ref:")) {
            return false;
        }
        return PLAINTEXT_PATTERN.matcher(value).matches();
    }
}
