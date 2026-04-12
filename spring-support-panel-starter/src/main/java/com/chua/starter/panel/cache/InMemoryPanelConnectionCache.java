package com.chua.starter.panel.cache;

import com.chua.starter.panel.model.PanelConnectionHandle;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 内存连接缓存。
 */
public class InMemoryPanelConnectionCache implements PanelConnectionCache {

    private final Duration ttl;
    private final Map<String, PanelConnectionHandle> cache = new ConcurrentHashMap<>();

    public InMemoryPanelConnectionCache(Duration ttl) {
        this.ttl = ttl;
    }

    @Override
    public void evict(String connectionId) {
        cache.remove(connectionId);
    }

    @Override
    public Collection<PanelConnectionHandle> handles() {
        return cache.values();
    }

    @Override
    public PanelConnectionHandle put(PanelConnectionHandle handle) {
        if (handle == null || handle.getConnectionId() == null) {
            return null;
        }
        LocalDateTime now = LocalDateTime.now();
        handle.setLastAccessTime(now);
        handle.setExpireTime(now.plus(ttl));
        cache.put(handle.getConnectionId(), handle);
        return handle;
    }

    @Override
    public PanelConnectionHandle remove(String connectionId) {
        return cache.remove(connectionId);
    }

    @Override
    public PanelConnectionHandle touch(String connectionId) {
        PanelConnectionHandle handle = cache.get(connectionId);
        if (handle == null) {
            return null;
        }
        LocalDateTime now = LocalDateTime.now();
        handle.setLastAccessTime(now);
        handle.setExpireTime(now.plus(ttl));
        return handle;
    }

    @Override
    public PanelConnectionHandle get(String connectionId) {
        PanelConnectionHandle handle = cache.get(connectionId);
        if (handle == null) {
            return null;
        }
        if (handle.getExpireTime() != null && handle.getExpireTime().isBefore(LocalDateTime.now())) {
            cache.remove(connectionId);
            return null;
        }
        return handle;
    }
}
