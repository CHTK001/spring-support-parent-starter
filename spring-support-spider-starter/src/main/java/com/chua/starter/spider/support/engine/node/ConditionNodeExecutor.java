package com.chua.starter.spider.support.engine.node;

import com.chua.starter.spider.support.domain.SpiderFlowNode;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;
import java.util.regex.Pattern;

/**
 * CONDITION 节点执行器。
 *
 * <p>支持四种条件类型（config["conditionType"]）：
 * <ul>
 *   <li>{@code field_compare}：比较记录中某字段值与期望值（操作符：eq/ne/gt/lt/gte/lte/contains）</li>
 *   <li>{@code status_code}：比较 HTTP 状态码（config["statusCode"]）</li>
 *   <li>{@code regex_match}：对字段值进行正则匹配（config["pattern"]）</li>
 *   <li>{@code ai_classify}：AI 分类（降级为 true，需外部 AI 集成）</li>
 * </ul>
 * </p>
 *
 * <p>返回 {@code true} 表示走 true 分支，{@code false} 表示走 false 分支。</p>
 *
 * @author CH
 */
@Slf4j
public class ConditionNodeExecutor {

    /**
     * 执行条件判断，返回 true/false 分支结果。
     *
     * @param node   CONDITION 节点定义
     * @param record 输入数据（Map 或任意对象）
     * @return {@code true} 走 true 分支，{@code false} 走 false 分支
     */
    public boolean evaluate(SpiderFlowNode node, Object record) {
        Map<String, Object> config = node.getConfig() != null ? node.getConfig() : Map.of();
        String conditionType = str(config, "conditionType", "field_compare");

        return switch (conditionType.toLowerCase()) {
            case "field_compare" -> evaluateFieldCompare(config, record);
            case "status_code"   -> evaluateStatusCode(config, record);
            case "regex_match"   -> evaluateRegexMatch(config, record);
            case "ai_classify"   -> evaluateAiClassify(config, record);
            default -> {
                log.warn("[CONDITION] nodeId={} 未知 conditionType={}，默认走 true 分支", node.getNodeId(), conditionType);
                yield true;
            }
        };
    }

    // ── field_compare ─────────────────────────────────────────────────────────

    private boolean evaluateFieldCompare(Map<String, Object> config, Object record) {
        String field    = str(config, "field", "");
        String operator = str(config, "operator", "eq");
        String expected = str(config, "value", "");

        Object actual = extractField(record, field);
        if (actual == null) {
            log.debug("[CONDITION][field_compare] 字段 {} 不存在，走 false 分支", field);
            return false;
        }

        String actualStr = actual.toString();
        return switch (operator.toLowerCase()) {
            case "eq"       -> actualStr.equals(expected);
            case "ne"       -> !actualStr.equals(expected);
            case "contains" -> actualStr.contains(expected);
            case "gt"       -> compareNumeric(actualStr, expected) > 0;
            case "lt"       -> compareNumeric(actualStr, expected) < 0;
            case "gte"      -> compareNumeric(actualStr, expected) >= 0;
            case "lte"      -> compareNumeric(actualStr, expected) <= 0;
            default -> {
                log.warn("[CONDITION][field_compare] 未知 operator={}，默认 false", operator);
                yield false;
            }
        };
    }

    // ── status_code ───────────────────────────────────────────────────────────

    private boolean evaluateStatusCode(Map<String, Object> config, Object record) {
        String expectedCode = str(config, "statusCode", "200");
        Object actual = extractField(record, "statusCode");
        if (actual == null) actual = extractField(record, "status_code");
        if (actual == null) {
            log.debug("[CONDITION][status_code] 记录中无 statusCode 字段，走 false 分支");
            return false;
        }
        return actual.toString().equals(expectedCode);
    }

    // ── regex_match ───────────────────────────────────────────────────────────

    private boolean evaluateRegexMatch(Map<String, Object> config, Object record) {
        String field   = str(config, "field", "");
        String pattern = str(config, "pattern", "");
        if (pattern.isBlank()) return false;

        Object actual = extractField(record, field);
        if (actual == null) return false;

        try {
            return Pattern.compile(pattern).matcher(actual.toString()).find();
        } catch (Exception e) {
            log.warn("[CONDITION][regex_match] 正则表达式非法 pattern={}: {}", pattern, e.getMessage());
            return false;
        }
    }

    // ── ai_classify ───────────────────────────────────────────────────────────

    private boolean evaluateAiClassify(Map<String, Object> config, Object record) {
        // AI 分类需要外部 SpiderBrainRuntime 集成，此处降级为 true
        log.info("[CONDITION][ai_classify] AI 分类暂未集成，默认走 true 分支");
        return true;
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private Object extractField(Object record, String field) {
        if (field == null || field.isBlank()) return null;
        if (record instanceof Map<?, ?> map) {
            return map.get(field);
        }
        return null;
    }

    private int compareNumeric(String a, String b) {
        try {
            return Double.compare(Double.parseDouble(a), Double.parseDouble(b));
        } catch (NumberFormatException e) {
            return a.compareTo(b);
        }
    }

    private String str(Map<String, Object> config, String key, String def) {
        Object v = config.get(key);
        return (v != null && !v.toString().isBlank()) ? v.toString() : def;
    }
}
