package com.chua.starter.panel.service;

import com.chua.starter.panel.model.PanelSqlTemplateRequest;

/**
 * 面板 SQL 模板服务。
 */
public interface PanelSqlTemplateService {

    String generateTemplate(String panelConnectionId, PanelSqlTemplateRequest request);
}
