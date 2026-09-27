package com.chua.starter.monitor.support.service;

import com.chua.starter.monitor.support.model.FileAssetContentVO;
import com.chua.starter.monitor.support.model.FileAssetItemVO;

import java.io.InputStream;
import java.util.List;

/**
 * 文件资产服务
 *
 * <p>面向 {@code FileStorage} SPI 提供目录浏览与对象读写，供前端「文件资产」页面以同源 REST
 * 方式访问，避免浏览器直连 HTTP 文件服务器带来的地址硬编码与跨源问题。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
public interface FileAssetService {

    /**
     * 列出目录下的条目。
     *
     * @param bucket 存储桶（Spring 容器中的 FileStorage Bean 名），为空取默认存储
     * @param path   目录路径，为空表示根目录
     * @param limit  最多返回条数
     * @return 条目列表
     */
    List<FileAssetItemVO> list(String bucket, String path, int limit);

    /**
     * 写入对象。
     *
     * @param bucket   存储桶，为空取默认存储
     * @param path     目标目录，为空表示根目录
     * @param fileName 文件名
     * @param content  内容流，由本方法负责关闭
     * @param size     字节数
     * @return 写入后的条目
     */
    FileAssetItemVO upload(String bucket, String path, String fileName, InputStream content, long size);

    /**
     * 读取对象。
     *
     * @param bucket 存储桶，为空取默认存储
     * @param path   对象路径
     * @return 内容，对象不存在时抛出异常
     */
    FileAssetContentVO download(String bucket, String path);

    /**
     * 删除对象。
     *
     * @param bucket 存储桶，为空取默认存储
     * @param path   对象路径
     * @return 是否删除成功
     */
    boolean delete(String bucket, String path);

    /**
     * 列出可用存储桶。
     *
     * @return 存储桶名称列表
     */
    List<String> buckets();
}
