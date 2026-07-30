package com.chua.starter.panel.service;

import com.chua.starter.panel.model.PanelSqlTemplateRequest;

/**
 * 面板 SQL 模板服务。
 *
 * @author CH
 */
public interface PanelSqlTemplateService {

    /**
     * 根据动作生成 SQL 模板。
     *
     * @param panelConnectionId 连接标识
     * @param request 模板参数
     * @return SQL 模板
     */
    String generateTemplate(String panelConnectionId, PanelSqlTemplateRequest request);
}
