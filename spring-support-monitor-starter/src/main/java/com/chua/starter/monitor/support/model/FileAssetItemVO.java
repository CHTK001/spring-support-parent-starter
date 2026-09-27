package com.chua.starter.monitor.support.model;

/**
 * 文件资产条目
 *
 * @param name         文件（目录）名
 * @param path         相对存储桶根目录的完整路径，不含前导斜杠
 * @param directory    是否为目录
 * @param size         字节数，目录为 0
 * @param lastModified 最后修改时间（毫秒时间戳），取不到时为 0
 * @author CH
 * @since 4.0.0.42
 */
public record FileAssetItemVO(
        String name,
        String path,
        boolean directory,
        long size,
        long lastModified
) {
}
