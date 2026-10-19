package com.chua.starter.filesystem.support.pojo;

/**
 * 单次上传结果。
 *
 * <p>对象键与访问地址均由服务端生成，调用方只拿到定位结果，不能指定落盘位置。</p>
 *
 * @param url         回显地址（可直接写库或用于 img 标签）
 * @param key         对象键（含桶名前缀）
 * @param bucket      存储桶
 * @param storageType 存储提供者名称
 * @param fileName    服务端生成的文件名
 * @param fileSize    字节数
 * @since 4.0.0.42
 * @author CH
 */
public record FileUploadResponse(
        String url,
        String key,
        String bucket,
        String storageType,
        String fileName,
        Long fileSize) {
}
