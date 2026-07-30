package com.chua.starter.panel.service;

import com.chua.starter.panel.model.PanelConnectionDefinition;
import com.chua.starter.panel.model.PanelConnectionDescriptor;
import com.chua.starter.panel.model.PanelConnectionHandle;

import java.util.List;

/**
 * 连接服务。
 *
 * @author CH
 */
public interface PanelConnectionService {

    /**
     * 逐出并关闭指定连接。
     *
     * @param connectionId 连接标识
     */
    void evict(String connectionId);

    /**
     * 查询已缓存的连接列表。
     *
     * @return 缓存连接列表
     */
    List<PanelConnectionDescriptor> listCachedConnections();

    /**
     * 打开连接并写入缓存。
     *
     * @param definition 连接定义
     * @return 连接句柄
     */
    PanelConnectionHandle open(PanelConnectionDefinition definition);

    /**
     * 获取连接句柄。
     *
     * @param connectionId 连接标识
     * @return 连接句柄；不存在时返回 null
     */
    PanelConnectionHandle get(String connectionId);
}
