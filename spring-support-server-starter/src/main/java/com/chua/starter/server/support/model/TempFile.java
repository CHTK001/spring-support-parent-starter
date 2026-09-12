package com.chua.starter.server.support.model;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 临时文件句柄
 * <p>
   * 聚合下载/编辑场景产生的本地临时文件，实现 auto关闭：
   * 使用方以 尝试-with-resources 持有，关闭即删除磁盘文件，
 * 防止敏感内容残留。
 * </p>
 *
 * @param path     临时文件路径
 * @param fileName 建议的展示文件名（通常为远端原始文件名）
 * @author CH
 * @since 4.0.0.42
 * @return temp文件的结果
 */
public record TempFile(Path path, String fileName) implements AutoCloseable {

    /**
     * 关闭并删除临时文件（幂等）
     */
    @Override
    public void close() {
        try {
            Files.deleteIfExists(path);
        } catch (IOException ignored) {
            // 删除失败交由操作系统临时目录清理兜底，不阻断主流程
        }
    }
}
