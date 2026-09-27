package com.chua.starter.filesystem.support.pojo;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

/**
 * 单次上传结果。
 *
 * <p>对象键与访问地址均由服务端生成，调用方只拿到定位结果，不能指定落盘位置。</p>
 *
 * @since 4.0.0.42
 * @author CH
 */
@Getter
@Setter
@Builder
public class FileUploadResponse {

    /**
     * 回显地址（可直接写库或用于 img 标签）
     */
    private String url;

    /**
     * 对象键（含桶名前缀）
     */
    private String key;

    /**
     * 存储桶
     */
    private String bucket;

    /**
     * 存储提供者名称
     */
    private String storageType;

    /**
     * 服务端生成的文件名
     */
    private String fileName;

    /**
     * 字节数
     */
    private Long fileSize;
}
