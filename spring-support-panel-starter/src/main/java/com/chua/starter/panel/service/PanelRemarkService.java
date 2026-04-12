package com.chua.starter.panel.service;

import com.chua.starter.panel.model.PanelRemarkRequest;
import com.chua.starter.panel.model.PanelRemarkView;

import java.util.List;

/**
 * 面板备注服务。
 */
public interface PanelRemarkService {

    List<PanelRemarkView> listByConnectionId(String panelConnectionId);

    PanelRemarkView saveRemark(PanelRemarkRequest request);
}
