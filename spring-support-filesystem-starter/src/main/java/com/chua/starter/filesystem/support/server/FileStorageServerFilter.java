package com.chua.starter.filesystem.support.server;

import com.chua.common.support.network.server.filter.ServerFilter;
import com.chua.common.support.network.server.filter.ServerFilterChain;
import com.chua.common.support.network.server.request.ServerRequest;
import com.chua.common.support.network.server.response.ServerResponse;
import com.chua.common.support.storage.FileStorage;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 文件存储服务器过滤器
 *
 * @since 2024/12/28
 * @author CH
 */
@Slf4j
public class FileStorageServerFilter implements ServerFilter {

    /**
     * 存储映射
     */
    private final Map<String, FileStorage> storageMap = new ConcurrentHashMap<>();

    /**
     * 文件存储设置
     */
    private final FileStorageSetting setting;

    public FileStorageServerFilter(FileStorageSetting setting) {
        this.setting = setting;
    }

    /**
     * 添加存储实例
     *
     * @param name    存储名称
     * @param storage 存储实例
     */
    public void addFileStorage(String name, FileStorage storage) {
        storageMap.put(name, storage);
    }

    @Override
    public void doFilter(ServerRequest request, ServerResponse response, ServerFilterChain chain) throws Exception {
        // 简单透传
        chain.doFilter(request, response);
    }
}