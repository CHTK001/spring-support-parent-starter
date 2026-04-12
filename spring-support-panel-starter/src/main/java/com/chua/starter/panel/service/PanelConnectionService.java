package com.chua.starter.panel.service;

import com.chua.starter.panel.model.PanelConnectionDefinition;
import com.chua.starter.panel.model.PanelConnectionDescriptor;
import com.chua.starter.panel.model.PanelConnectionHandle;

import java.util.List;

/**
 * 连接服务。
 */
public interface PanelConnectionService {

    void evict(String connectionId);

    List<PanelConnectionDescriptor> listCachedConnections();

    PanelConnectionHandle open(PanelConnectionDefinition definition);

    PanelConnectionHandle get(String connectionId);
}
