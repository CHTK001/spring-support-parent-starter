package com.chua.starter.panel.cache;

import com.chua.starter.panel.model.PanelConnectionHandle;

import java.util.Collection;

/**
 * 连接缓存。
 */
public interface PanelConnectionCache {

    void evict(String connectionId);

    Collection<PanelConnectionHandle> handles();

    PanelConnectionHandle put(PanelConnectionHandle handle);

    PanelConnectionHandle remove(String connectionId);

    PanelConnectionHandle touch(String connectionId);

    PanelConnectionHandle get(String connectionId);
}
