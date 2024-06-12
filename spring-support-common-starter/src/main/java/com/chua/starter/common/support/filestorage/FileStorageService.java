package com.chua.starter.common.support.filestorage;

import com.chua.common.support.oss.FileStorage;
import com.chua.common.support.spi.Option;

import java.util.List;
import java.util.Set;

/**
 * 文件存储服务
 *
 * @author CH
 */
public interface FileStorageService {
    /**
     * 包含密钥
     *
     * @param bucket 水桶
     * @return boolean
     */
    boolean containsKey(String bucket);

    /**
     * 收到
     *
     * @param bucket 水桶
     * @return {@link FileStorage}
     */
    FileStorage get(String bucket);

    /**
     * 注册
     *
     * @param bucket      水桶
     * @param fileStorage 文件存储器
     */
    void register(String bucket, FileStorage fileStorage);

    /**
     * 注销
     *
     * @param bucket 水桶
     */
    void unregister(String bucket);

    /**
     * 获取类型
     *
     * @return {@link Set}<{@link String}>
     */
    List<Option<String>> getType();

    /**
     * 获取图片滤镜
     *
     * @return {@link List}<{@link Option}<{@link String}>>
     */
    List<Option<String>> getFilter();

    /**
     * 获得命名
     *
     * @return {@link List}<{@link Option}<{@link String}>>
     */
    List<Option<String>> getNamed();


}
