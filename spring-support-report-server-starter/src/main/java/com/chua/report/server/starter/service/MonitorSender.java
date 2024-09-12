package com.chua.report.server.starter.service;

import com.chua.common.support.discovery.Discovery;
import com.chua.report.client.starter.endpoint.ModuleType;
import com.chua.starter.common.support.project.Project;

/**
 * 监控发送
 * @author CH
 * @since 2024/9/12
 */
public interface MonitorSender {
    void upload(Object o, Discovery discovery, String params, ModuleType type);
    Project getProject(Discovery discovery);
}
