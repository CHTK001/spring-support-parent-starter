package com.chua.starter.panel.service;

import com.chua.starter.panel.model.PanelDatasourceRequest;
import com.chua.starter.panel.model.PanelDriverUploadView;
import com.chua.starter.panel.model.PanelDatasourceView;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * Panel 数据源服务。
 */
public interface PanelDatasourceService {

    List<PanelDatasourceView> listAll();

    PanelDatasourceView save(PanelDatasourceRequest request);

    PanelDriverUploadView uploadDriver(String panelDialectType, MultipartFile file);

    void delete(String panelSourceId);
}
