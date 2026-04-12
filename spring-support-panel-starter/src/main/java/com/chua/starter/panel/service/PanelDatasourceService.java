package com.chua.starter.panel.service;

import com.chua.starter.panel.model.PanelDatasourceRequest;
import com.chua.starter.panel.model.PanelDatasourceView;

import java.util.List;

/**
 * Panel 数据源服务。
 */
public interface PanelDatasourceService {

    List<PanelDatasourceView> listAll();

    PanelDatasourceView save(PanelDatasourceRequest request);

    void delete(String panelSourceId);
}
