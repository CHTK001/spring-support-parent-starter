package com.chua.starter.filesystem.support.spi;

/**
 * 远程文件读取结果。
 *
 * <p>包含文件字节内容、文件名与推断的 MIME 类型。
 * 过滤器拿到结果后走与本地对象完全相同的预览分发管线。</p>
 *
 * @param fileName    文件名（用于 MIME 类型推断与前端渲染器选择）
 * @param contentType MIME 类型（如 {@code application/pdf}）
 * @param data        文件字节内容
 * @author CH
 * @since 4.0.0.43
 */
public record RemoteFileResult(
        /**
         * 文件名
         */
        String fileName,
        /**
         * MIME 类型
         */
        String contentType,
        /**
         * 文件字节内容
         */
        byte[] data
) {
}
