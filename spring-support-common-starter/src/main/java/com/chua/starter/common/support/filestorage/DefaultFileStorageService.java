package com.chua.starter.common.support.filestorage;

import com.chua.common.support.image.filter.ImageFilter;
import com.chua.common.support.oss.FileStorage;
import com.chua.common.support.oss.plugin.FileStorageConversionPlugin;
import com.chua.common.support.oss.plugin.FileStorageNamingPlugin;
import com.chua.common.support.spi.Option;
import com.chua.common.support.spi.ServiceProvider;
import com.chua.common.support.utils.IoUtils;

import java.util.List;
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

    @Override
    public void register(String bucket, FileStorage fileStorage) {
        unregister(bucket);
        storageMap.put(bucket, fileStorage);
    }

    @Override
    public void unregister(String bucket) {
        FileStorage fileStorage = get(bucket);
        if(null != fileStorage) {
            IoUtils.closeQuietly(fileStorage);
        }

        storageMap.remove(bucket);
    }

    @Override
    public List<Option<String>> getType() {
        return ServiceProvider.of(FileStorage.class).options();
    }

    @Override
    public List<Option<String>> getFilter() {
        return ServiceProvider.of(ImageFilter.class).options();
    }

    @Override
    public List<Option<String>> getPlugin() {
        return ServiceProvider.of(FileStorageConversionPlugin.class).options();
    }

    @Override
    public List<Option<String>> getNamed() {
        return ServiceProvider.of(FileStorageNamingPlugin.class).options();
    }

}
