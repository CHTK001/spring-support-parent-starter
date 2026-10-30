package com.chua.starter.docker.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;

/**
 * JSON 文件存储（UTF-8 无 BOM，临时文件 + move 落盘）。
 *
 * <p>镜像库与模板的持久化都走这里：写入先落同目录临时文件再原子替换，避免进程
 * 中断留下半截 JSON；同一文件的读改写由 {@link #lock} 串行化，读写方法本身也
 * 会加锁（{@link ReentrantLock} 可重入，调用方可在外层持有锁完成「读—改—写」）。</p>
 *
 * @author CH
 * @since 4.0.0.45
 */
@Slf4j
final class JsonFileStore {

    /**
     * 目标文件
     */
    private final Path file;

    /**
     * JSON 读写器
     */
    private final ObjectMapper mapper;

    /**
     * 文件级互斥锁
     */
    final ReentrantLock lock = new ReentrantLock();

    /**
     * 构造存储
     * @param file 目标文件
     * @param mapper JSON 读写器
     * @return 返回值
    */
    JsonFileStore(Path file, ObjectMapper mapper) {
        this.file = file;
        this.mapper = mapper;
    }

    /**
     * 读取 JSON 数组；文件不存在、为空或损坏时返回空列表。
     *
     * @param type 元素类型
     * @param <T>  元素类型
     * @return 列表（非 null）
     */
    <T> List<T> readList(Class<T> type) {
        lock.lock();
        try {
            if (!Files.exists(file)) {
                return new ArrayList<>();
            }
            String text = Files.readString(file, StandardCharsets.UTF_8);
            if (text.isBlank()) {
                return new ArrayList<>();
            }
            return mapper.readValue(text, mapper.getTypeFactory()
                    .constructCollectionType(List.class, type));
        } catch (Exception e) {
            log.warn("[docker] 读取 {} 失败: {}", file, e.getMessage());
            return new ArrayList<>();
        } finally {
            lock.unlock();
        }
    }

    /**
     * 写入 JSON 数组（临时文件 + move）。
     *
     * @param value 待写入对象
     */
    void write(Object value) {
        lock.lock();
        Path tmp = null;
        try {
            Path dir = file.getParent();
            if (dir != null) {
                Files.createDirectories(dir);
            }
            tmp = Files.createTempFile(dir, file.getFileName().toString(), ".tmp");
            mapper.writerWithDefaultPrettyPrinter().writeValue(tmp.toFile(), value);
            try {
                Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
            }
            tmp = null;
        } catch (Exception e) {
            log.warn("[docker] 写入 {} 失败: {}", file, e.getMessage());
            throw new IllegalStateException("保存配置失败: " + e.getMessage(), e);
        } finally {
            if (tmp != null) {
                try {
                    Files.deleteIfExists(tmp);
                } catch (Exception ignored) {
                    // 临时文件清理失败不影响主流程
                }
            }
            lock.unlock();
        }
    }
}
