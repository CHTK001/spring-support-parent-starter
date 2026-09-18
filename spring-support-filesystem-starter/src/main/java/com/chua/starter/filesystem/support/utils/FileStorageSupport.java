package com.chua.starter.filesystem.support.utils;

import com.chua.common.support.spi.ServiceProvider;
import com.chua.common.support.storage.setting.BucketSetting;
import com.chua.common.support.storage.FileStorage;

/**
* 文件存储工具类。
*
* @since 1.0
* @author CH
 */
public final class FileStorageSupport {

    /** 创建 文件storage支持 实例 */
    private FileStorageSupport() {
    }

    /**
    * 通过 SPI 创建文件存储实例。
    *
    * @param name 存储类型名称
    * @param bucketSetting 存储配置
    * @return 文件存储实例
    */
    public static FileStorage createStorage(String name, BucketSetting bucketSetting) {
        return ServiceProvider.of(FileStorage.class)
                .getNewExtension(name, bucketSetting);
    }

    /**
    * 创建本地存储桶配置。
    *
    * @param bucket 存储桶名称
    * @param path 存储路径
    * @return 存储桶配置
    */
    public static BucketSetting createLocalBucketSetting(String bucket) {
        return BucketSetting.builder()
                .bucket(bucket)
                .build();
    }
}
