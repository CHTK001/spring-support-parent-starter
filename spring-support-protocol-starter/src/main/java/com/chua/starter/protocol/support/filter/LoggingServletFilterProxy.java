package com.chua.starter.protocol.support.filter;

import com.chua.common.support.time.date.DateUtils;
import com.chua.common.support.network.protocol.event.ServletEvent;
import com.chua.common.support.network.protocol.filter.ServletFilter;
import com.chua.common.support.network.protocol.request.ServletRequest;
import com.chua.common.support.utils.StringUtils;
import com.chua.spring.support.configuration.SpringBeanUtils;
import com.chua.starter.protocol.support.entity.SystemServerLog;
import com.chua.starter.protocol.support.entity.SystemServerSetting;
import com.chua.starter.protocol.support.service.server.IptablesService;
import com.chua.starter.protocol.support.service.server.SystemServerLogService;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;
import java.lang.reflect.Method;
import java.util.Map;

/**
 * ServletFilter 执行日志代理
 * <p>
 * 通过 JDK 动态代理拦截 Filter 的执行方法，采集并异步落库，不影响业务主流程。
 *
 * @author CH
 * @since 2025/04/05
 */
@Slf4j
public class LoggingServletFilterProxy {

    /**
     * 包装并记录ServletFilter的执行日志
     *
     * @param setting       系统服务器配置信息，用于关联日志记录的服务器ID等信息
     *                      例如: new SystemServerSetting() {{ setSystemServerSettingId(1); }}
     * @param servletFilter 被包装的Servlet过滤器实例，用于获取过滤器类型等信息
     *                      例如: new MyCustomServletFilter()
     * @param event         Servlet事件对象，包含请求、响应及处理状态等信息
     *                      例如: new ServletEvent(request, response, status)
     */
    public static void wrap(SystemServerSetting setting, ServletFilter servletFilter, ServletEvent event) {
        ServletRequest request = event.getRequest();
        SystemServerLog logItem = new SystemServerLog();
        // 注意：日志按 serverId 聚合，不能误写为 settingId
        logItem.setServerId(setting.getSystemServerSettingServerId());
        logItem.setFilterType(servletFilter.getClass().getName());
        String statusName = event.getStatus() == null ? "" : event.getStatus().name();
        logItem.setProcessStatus(statusName);
        logItem.setActionStatus(toActionStatus(statusName));
        logItem.setClientIp(event.getClientIp());
        logItem.setUserAgent(extractHeader(request, "User-Agent"));
        logItem.setReferer(resolveReferer(request));
        logItem.setRequestId(StringUtils.isEmpty(event.getRequestId()) ? "" : event.getRequestId());
        logItem.setMethod(StringUtils.isEmpty(event.getMethod()) ? extractHeader(request, "X-FTP-Command") : event.getMethod());
        logItem.setPath(StringUtils.isEmpty(event.getPath()) ? "" : event.getPath());
        logItem.setHost(resolveHost(request));
        logItem.setStatusCode(resolveStatusCode(event));

        Map<String, Object> meta = event.getMetadata();
        logItem.setRuleCategory(resolveRuleCategory(statusName, meta));
        logItem.setDecisionReason(resolveDecisionReason(meta));
        logItem.setAccessTime(event.getTimestamp());
        long durationMs = (System.currentTimeMillis() - DateUtils.toEpochMilli(logItem.getAccessTime()));
        logItem.setDurationMs(durationMs);
        logItem.setStoreTime(LocalDateTime.now());
        String clientIp = event.getClientIp();
        // 解析地理位置（仅当有 IP 时）
        if (!StringUtils.isEmpty(clientIp)) {
            try {
                IptablesService iptablesService = SpringBeanUtils.getBean(IptablesService.class);
                if (iptablesService != null) {
                    var rr = iptablesService.transferAddress(clientIp);
                    if (rr != null && rr.isSuccess() && rr.getData() != null) {
                        var city = rr.getData();
                        String geo = String.join(" ",
                                defaultIfEmpty(city.getCountry(), ""),
                                defaultIfEmpty(city.getProvince(), ""),
                                defaultIfEmpty(city.getCity(), ""));
                        logItem.setClientGeo(geo.trim());
                    }
                }
            } catch (Throwable ge) {
                // 忽略地理位置失败
            }
        }
        SystemServerLogService service = SpringBeanUtils.getBean(SystemServerLogService.class);
        if (service != null) {
            service.asyncRecord(logItem);
        }
    }

    private static String resolveRuleCategory(String processStatus, Map<String, Object> meta) {
        String fromMeta = meta == null ? "" : string(meta.get("ruleCategory"));
        if (!StringUtils.isEmpty(fromMeta)) {
            return fromMeta.toUpperCase();
        }
        String up = processStatus == null ? "" : processStatus.toUpperCase();
        if (up.contains("BLACKLIST")) return "BLACKLIST";
        if (up.contains("WHITELIST")) return "WHITELIST";
        if (up.contains("LIMIT")) return "RATE_LIMIT";
        return "UNKNOWN";
    }

    private static String resolveDecisionReason(Map<String, Object> meta) {
        if (meta == null || meta.isEmpty()) {
            return "";
        }
        String reason = string(meta.get("reason"));
        if (!StringUtils.isEmpty(reason)) return reason;
        String message = string(meta.get("message"));
        if (!StringUtils.isEmpty(message)) return message;
        return "";
    }

    private static String string(Object v) {
        return v == null ? "" : String.valueOf(v);
    }

    private static String toActionStatus(String statusName) {
        if (StringUtils.isEmpty(statusName)) {
            return "";
        }
        String up = statusName.toUpperCase();
        if ("OK".equals(up) || up.contains("PASS") || up.contains("ALLOW")) {
            return "PASS";
        }
        if (up.contains("BLOCK") || up.contains("DENY") || up.contains("REJECT") || up.contains("LIMIT")) {
            return "DENY";
        }
        // 默认保守：未知状态按拒绝展示，避免误判为通行
        return "DENY";
    }

    private static String extractHeader(ServletRequest request, String headerName) {
        if (request == null || StringUtils.isEmpty(headerName)) {
            return "";
        }
        try {
            Method m = request.getClass().getMethod("getHeader", String.class);
            Object v = m.invoke(request, headerName);
            return v == null ? "" : String.valueOf(v);
        } catch (Throwable ignore) {
            // 兼容不同 ServletRequest 实现（不强依赖某个方法签名）
        }
        return "";
    }

    private static String resolveHost(ServletRequest request) {
        if (request == null) {
            return "";
        }
        String fromHeader = extractHeader(request, "Host");
        if (!StringUtils.isEmpty(fromHeader)) {
            return fromHeader;
        }
        try {
            String serverName = request.getServerName();
            if (!StringUtils.isEmpty(serverName)) {
                return serverName;
            }
        } catch (Throwable ignore) {
        }
        try {
            String url = request.getUrl();
            if (!StringUtils.isEmpty(url)) {
                java.net.URI uri = java.net.URI.create(url);
                return StringUtils.nullToDefault(uri.getHost(), "");
            }
        } catch (Throwable ignore) {
        }
        return "";
    }

    private static String resolveReferer(ServletRequest request) {
        if (request == null) {
            return "";
        }
        String referer = extractHeader(request, "Referer");
        if (!StringUtils.isEmpty(referer)) {
            return referer;
        }
        try {
            return StringUtils.nullToDefault(request.getReferer(), "");
        } catch (Throwable ignore) {
            return "";
        }
    }

    private static Integer resolveStatusCode(ServletEvent event) {
        if (event == null) {
            return null;
        }
        if (event.getStatusCode() != null && event.getStatusCode() > 0) {
            return event.getStatusCode();
        }
        try {
            if (event.getResponse() != null && event.getResponse().getStatusCode() > 0) {
                return event.getResponse().getStatusCode();
            }
        } catch (Throwable ignore) {
        }
        return null;
    }


    /**
     * 如果字符串为空则返回默认值
     *
     * @param v   待检查的字符串
     *            例如: ""
     * @param def 默认值
     *            例如: "Unknown"
     * @return 如果v为空则返回def，否则返回v本身
     */
    private static String defaultIfEmpty(String v, String def) {
        return StringUtils.isEmpty(v) ? def : v;
    }
}
