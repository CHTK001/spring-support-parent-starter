package com.chua.starter.common.support.filestorage;

import com.chua.common.support.oss.FileStorage;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 文件存储服务
 *
 * @author CH
 */
public class DefaultFileStorageService implements FileStorageService{

    private final Map<String, FileStorage> storageMap = new ConcurrentHashMap<>();

    @Override
    public boolean containsKey(String bucket) {
        return storageMap.containsKey(bucket);
    }

    @Override
    public FileStorage get(String bucket) {
        return storageMap.get(bucket);
    }
}
