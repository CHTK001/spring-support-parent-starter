package com.chua.starter.server.support.terminal;

import org.springframework.web.socket.WebSocketSession;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * WebSocket 会话公共解析
 * <p>
 * 直连通道（SSH 与 VNC）都要从握手 URL 取参数、取来源 地址、
 * 并把底层库的包装异常还原成可读原因，这里统一承载，避免两处各写一份。
 * </p>
 *
 * @author CH
 * @since 4.0.0.43
 */
public final class WebSocketSessions {

    /**
     * 工具类禁止实例化
     */
    private WebSocketSessions() {
    }

    /**
     * 解析握手 URL 的查询参数
     *
     * @param session WebSocket 会话
     * @return 参数表，无查询串时为空表
     */
    public static Map<String, String> parseQuery(WebSocketSession session) {
        URI uri = session.getUri();
        Map<String, String> params = new HashMap<>();
        if (uri == null || uri.getRawQuery() == null) {
            return params;
        }
        for (String pair : uri.getRawQuery().split("&")) {
            int idx = pair.indexOf('=');
            if (idx <= 0) {
                continue;
            }
            params.put(java.net.URLDecoder.decode(pair.substring(0, idx), StandardCharsets.UTF_8),
                    java.net.URLDecoder.decode(pair.substring(idx + 1), StandardCharsets.UTF_8));
        }
        return params;
    }

    /**
     * 安全解析长整型参数
     *
     * @param value 原始字符串
     * @return 长整型，非法或空时为空
     */
    public static Long parseLong(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Long.valueOf(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * 安全解析整型参数
     *
     * @param value 原始字符串
     * @return 整型，非法或空时为空
     */
    public static Integer parseInteger(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Integer.valueOf(value.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * 安全解析正整型参数
     *
     * @param value      原始字符串
     * @param defaultVal 兜底值
     * @return 正整型，非法或非正数时取兜底
     */
    public static int parsePositiveInt(String value, int defaultVal) {
        Integer parsed = parseInteger(value);
        return parsed != null && parsed > 0 ? parsed : defaultVal;
    }

    /**
     * 提取浏览器来源 IP
     *
     * @param session WebSocket 会话
     * @return IP 字符串，取不到时返回 unknown
     */
    public static String clientIp(WebSocketSession session) {
        var remoteAddress = session.getRemoteAddress();
        return remoteAddress == null ? "unknown" : remoteAddress.getAddress().getHostAddress();
    }

    /**
     * 还原异常链最内层的可读原因
     * <p>
     * 底层库的包装层常只带「连接失败: 地址」这类无信息量文案，真实故障（认证被拒、
     * 密钥或口令不可用、协议不匹配）位于最内层，不透出则页面无从判断。
     * </p>
     *
     * @param error 异常
     * @return 最内层原因文本
     */
    public static String describeCause(Throwable error) {
        Throwable root = error;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        String message = root.getMessage();
        if (message == null || message.isBlank()) {
            return root.getClass().getSimpleName();
        }
        String simple = root.getClass().getSimpleName();
        return message.startsWith(simple) ? message : simple + ": " + message;
    }

    /**
     * 取第一个非空白候选值
     * <p>
     * 审计的「用户」列既可能由调用方显式传入，也可能只能退回凭据登录账户，
     * 三条通道共用该兜底口径，避免网页会话记成 anonymous。
     * </p>
     *
     * @param preferred 优先值
     * @param fallback  兜底值
     * @return 优先值非空白时取优先值，否则取兜底值
     */
    public static String firstNonBlank(String preferred, String fallback) {
        return preferred == null || preferred.isBlank() ? fallback : preferred;
    }

    /**
     * 转义文本中的 JSON 特殊字符
     *
     * @param value 原始文本
     * @return 转义后文本，入参为空时返回空串
     */
    public static String escapeJson(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", " ").replace("\r", " ");
    }
}
