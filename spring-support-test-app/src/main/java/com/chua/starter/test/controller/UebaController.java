package com.chua.starter.test.controller;

import com.chua.ueba.support.dto.UebaResult;
import com.chua.ueba.support.engine.UebaEngine;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * UEBA 用户实体行为分析演示控制器。
 *
 * <p>接收 HTTP 访问日志模拟流量事件，调用 {@link UebaEngine} 返回综合风险等级与解释。</p>
 *
 * <p>请求示例：</p>
 * <pre>
 * POST /api/ueba/analyze
 * {
 *   "ip": "10.0.0.1",
 *   "path": "/index",
 *   "method": "GET",
 *   "statusCode": 200,
 *   "userAgent": "Mozilla/5.0",
 *   "responseTimeMs": 50
 * }
 * </pre>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
@RestController
@RequestMapping("/api/ueba")
public class UebaController {

    /**
     * UEBA 分析引擎（由 Spring 自动装配）。
     */
    private final UebaEngine engine;

    /**
     * 构造控制器。
     *
     * @param engine UEBA 分析引擎，不能为 空
     */
    public UebaController(UebaEngine engine) {
        if (engine == null) {
            /**
             * illegal参数异常。
             * @param null" 空"
             * @return illegal参数异常的结果
             */
            throw new IllegalArgumentException("engine must not be null");
        }
        this.engine = engine;
    }

    /**
     * 分析单条流量事件，返回综合风险评分。
     *
     * @param request 请求体，字段对应 traffic事件 关键字段
     * @return 风险等级、分数与解释
     */
    @PostMapping("/analyze")
    public Map<String, Object> analyze(@RequestBody Map<String, Object> request) {
        String ip = toString(request.get("ip"));
        String path = toString(request.get("path"));
        String method = toString(request.get("method"));
        int status = toInt(request.get("status"));
        String userAgent = toString(request.get("userAgent"));
        int responseTimeMs = toInt(request.get("responseTime"));
        long timestamp = toLong(request.get("timestamp"));

        log.info("[UEBA-Controller] analyzing event: ip={}, path={}, method={}, status={}",
                ip, path, method, status);

 // 构建内部 traffic事件（简化字段映射）
        com.chua.ueba.support.dto.TrafficEvent event =
                com.chua.ueba.support.dto.TrafficEvent.builder()
                        .ip(ip)
                        .path(path)
                        .method(method)
                        .statusCode(status)
                        .userAgent(userAgent)
                        .timestamp(timestamp > 0 ? timestamp : System.currentTimeMillis())
                        .responseTimeMs(responseTimeMs)
                        .responseSizeBytes(0L)
                        .build();

        UebaResult result = engine.analyze(event);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("entityId", result.getEntityId());
        response.put("riskLevel", result.getRiskLevel());
        response.put("riskScore", result.getRiskScore());
        response.put("ipAnomaly", toMap(result.getIpAnomaly()));
        response.put("behavior", toMap(result.getBehavior()));
        response.put("explanation", result.getExplanation());
        response.put("analyzedAt", result.getAnalyzedAt());
        return response;
    }

    /**
      * 将 ipanomaly结果 转为 映射（JSON 友好）。
     * @param obj obj
     * @return 转为映射的结果
     */
    private Map<String, Object> toMap(Object obj) {
        if (obj == null) {
            return Map.of();
        }
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("ip", getProp(obj, "ip"));
        map.put("reconstructionError", getProp(obj, "reconstructionError"));
        map.put("anomalous", getProp(obj, "anomalous"));
        map.put("level", getProp(obj, "level"));
        map.put("reason", getProp(obj, "reason"));
        return map;
    }

    /**
      * 将 行为配置文件 转为 映射。
     * @param b b
     * @return 行为转为映射的结果
     */
    private Map<String, Object> behaviorToMap(com.chua.ueba.support.dto.BehaviorProfile b) {
        if (b == null) {
            return Map.of();
        }
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("entityId", b.getEntityId());
        map.put("predictedClass", b.getPredictedClass());
        map.put("label", b.getLabel());
        map.put("confidence", b.getConfidence());
        map.put("recentPaths", b.getRecentPaths());
        map.put("riskScore", b.getRiskScore());
        map.put("analysisMode", b.getAnalysisMode());
        return map;
    }

    /**
     * 安全获取简单属性值。
     * @param obj obj
     * @param name 名称
     * @return 获取prop的结果
     */
    private static Object getProp(Object obj, String name) {
        try {
            java.lang.reflect.Method m = obj.getClass().getMethod("get" + capitalize(name));
            return m.invoke(obj);
        } catch (Exception ignored) {
            return null;
        }
    }

    /**
     * 转为字符串。
     * @param v v
     * @return 转为字符串的结果
     */
    private static String toString(Object v) {
        return v == null ? "" : String.valueOf(v);
    }

    /**
     * 转为int。
     * @param v v
     * @return 转为int的结果
     */
    private static int toInt(Object v) {
        if (v == null) {
            return 0;
        }
        if (v instanceof Number n) {
            return n.intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(v));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /**
     * 转为long。
     * @param v v
     * @return 转为long的结果
     */
    private static long toLong(Object v) {
        if (v == null) {
            return 0L;
        }
        if (v instanceof Number n) {
            return n.longValue();
        }
        try {
            return Long.parseLong(String.valueOf(v));
        } catch (NumberFormatException e) {
            return 0L;
        }
    }

    /**
     * capitalize。
     * @param s s
     * @return capitalize的结果
     */
    private static String capitalize(String s) {
        if (s == null || s.isEmpty()) {
            return s;
        }
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}