package com.chua.starter.proxy.support.filter;

import com.chua.common.support.time.date.DateUtils;
import com.chua.common.support.network.protocol.event.ServletEvent;
import com.chua.common.support.network.protocol.filter.ServletFilter;
import com.chua.common.support.network.protocol.request.ServletRequest;
import com.chua.common.support.core.utils.StringUtils;
import com.chua.spring.support.configuration.SpringBeanUtils;
import com.chua.starter.proxy.support.entity.SystemServerLog;
import com.chua.starter.proxy.support.entity.SystemServerSetting;
import com.chua.starter.common.support.service.IptablesService;
import com.chua.starter.proxy.support.service.server.SystemServerLogService;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;

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
        logItem.setServerId(setting.getSystemServerSettingId());
        logItem.setFilterType(servletFilter.getClass().getName());
        logItem.setProcessStatus(event.getStatus().name());
        logItem.setClientIp(event.getClientIp());
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





