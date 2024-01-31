package com.chua.starter.monitor.server.router;

import com.chua.common.support.annotations.OnRouterEvent;
import com.chua.common.support.utils.ClassUtils;
import com.chua.starter.monitor.request.MonitorRequest;
import com.chua.starter.monitor.request.MonitorRequestType;
import lombok.Data;

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 路由器
 *
 * @author CH
 * @version 1.0.0
 * @since 2024/01/31
 */
public class Router {

    private final Map<String, List<RouterInfo>> routerInfoMap = new ConcurrentHashMap<>();
    public void addRoute(OnRouterEvent onRouterEvent, Object bean, Method method) {
        ClassUtils.setAccessible(method);
        String[] value = onRouterEvent.value();
        for (String string : value) {
            routerInfoMap.computeIfAbsent(string.toUpperCase(), it -> new LinkedList<>()).add(new RouterInfo(onRouterEvent, bean, method));
        }
    }

    public void doRoute(MonitorRequest monitorRequest) {
        MonitorRequestType monitorRequestType = monitorRequest.getType();
        routerInfoMap.getOrDefault(monitorRequestType.getName().toUpperCase(), Collections.emptyList()).forEach(it -> {
            try {
                it.getMethod().invoke(it.getBean(), monitorRequest);
            } catch (Exception ignored) {
            }
        });
    }


    @Data
    class RouterInfo {

        private final OnRouterEvent onRouterEvent;
        private final Object bean;
        private final Method method;

        public RouterInfo(OnRouterEvent onRouterEvent, Object bean, Method method) {
            this.onRouterEvent = onRouterEvent;
            this.bean = bean;
            this.method = method;
        }
    }
}
