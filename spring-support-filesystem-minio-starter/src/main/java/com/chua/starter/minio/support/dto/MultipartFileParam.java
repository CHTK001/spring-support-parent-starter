package com.chua.starter.minio.support.dto;

import org.springframework.web.multipart.MultipartFile;

/**
 * 分块文件
 * @author CH
 */
public class MultipartFileParam {
    /**
     * 任务ID
     */
    private String taskId;
    /**
     * 当前为第几分片
     */
    private int chunkNumber;
    /**
     * 每个分块的大小
     */
    private long chunkSize;
    /**
     * 分片总数
     */
    private int totalChunks;
    /**
     * 文件唯一标识
     */
    private String identifier;

    /**
     * 分块文件传输对象
     */
    private MultipartFile file;
}
