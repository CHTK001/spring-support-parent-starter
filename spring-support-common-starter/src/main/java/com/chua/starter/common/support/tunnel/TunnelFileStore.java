package com.chua.starter.common.support.tunnel;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * 基于文件目录的隧道配置存储器（一条映射一个 JSON 文件）。
 *
 * <p>定位：内网穿透模块只需要「少量、低频写、可人工查看」的配置，不值得引入数据库。
 * 因此按 {@code <dir>/<id>.json} 保存，写入使用「临时文件 + 原子替换」，
 * 避免进程中断留下半个 JSON；读取时坏文件只告警跳过，不影响其余映射。</p>
 *
 * <p>并发：所有公开方法均为 {@code synchronized}，页面高频刷新与启停操作共用同一把锁，
 * 保证内存缓存与磁盘一致。</p>
 *
 * @author CH
 * @since 4.0.0.52
 */
@Slf4j
public class TunnelFileStore {

    /**
     * 合法标识：UUID 十六进制或外部传入的安全短标识，杜绝目录穿越。
     */
    private static final Pattern SAFE_ID = Pattern.compile("[A-Za-z0-9_-]{1,64}");

    /**
     * 存储目录。
     */
    private final Path dir;

    /**
     * JSON 序列化器。
     */
    private final ObjectMapper mapper;

    /**
     * 内存缓存：id → 记录。
     */
    private final ConcurrentHashMap<String, TunnelRecord> cache = new ConcurrentHashMap<>();

    /**
     * 创建存储器并加载已有文件。
     *
     * @param dir    存储目录
     * @param mapper JSON 序列化器
     */
    public TunnelFileStore(Path dir, ObjectMapper mapper) {
        this.dir = dir.toAbsolutePath().normalize();
        this.mapper = mapper;
        reload();
    }

    /**
     * 存储目录。
     *
     * @return 绝对路径
     */
    public Path getDir() {
        return dir;
    }

    /**
     * 生成一个新的合法标识。
     *
     * @return 32 位十六进制标识
     */
    public static String newId() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    /**
     * 全部记录，按更新时间倒序。
     *
     * @return 记录列表
     */
    public synchronized List<TunnelRecord> findAll() {
        List<TunnelRecord> records = new ArrayList<>(cache.values());
        records.sort(Comparator.comparingLong(TunnelRecord::getUpdatedAt).reversed());
        return records;
    }

    /**
     * 按标识查询。
     *
     * @param id 标识
     * @return 记录（不存在时为空）
     */
    public synchronized Optional<TunnelRecord> find(String id) {
        if (!isSafeId(id)) {
            return Optional.empty();
        }
        return Optional.ofNullable(cache.get(id));
    }

    /**
     * 保存记录（新增或覆盖），并立即落盘。
     *
     * @param record 记录
     * @return 保存后的记录
     */
    public synchronized TunnelRecord save(TunnelRecord record) {
        if (record.getId() == null || record.getId().isBlank()) {
            record.setId(newId());
        }
        if (!isSafeId(record.getId())) {
            throw new IllegalArgumentException("非法的隧道标识: " + record.getId());
        }
        long now = System.currentTimeMillis();
        if (record.getCreatedAt() <= 0) {
            record.setCreatedAt(now);
        }
        record.setUpdatedAt(now);
        write(record);
        cache.put(record.getId(), record);
        return record;
    }

    /**
     * 删除记录及对应文件。
     *
     * @param id 标识
     * @return 被删除的记录（不存在时为空）
     */
    public synchronized Optional<TunnelRecord> delete(String id) {
        if (!isSafeId(id)) {
            return Optional.empty();
        }
        TunnelRecord removed = cache.remove(id);
        try {
            Files.deleteIfExists(fileOf(id));
        } catch (IOException e) {
            log.warn("[Tunnel] 删除映射文件失败: {} ({})", fileOf(id), e.getMessage());
        }
        return Optional.ofNullable(removed);
    }

    /**
     * 重新从磁盘加载（启动时以及外部手工改文件后的兜底）。
     *
     * @return 加载到的记录数
     */
    public synchronized int reload() {
        cache.clear();
        if (!Files.isDirectory(dir)) {
            return 0;
        }
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir, "*.json")) {
            for (Path file : stream) {
                String fileName = file.getFileName().toString();
                String id = fileName.substring(0, fileName.length() - ".json".length());
                if (!isSafeId(id)) {
                    log.warn("[Tunnel] 跳过非法文件名: {}", file);
                    continue;
                }
                try {
                    TunnelRecord record = mapper.readValue(Files.readString(file, StandardCharsets.UTF_8),
                            TunnelRecord.class);
                    record.setId(id);
                    cache.put(id, record);
                } catch (IOException e) {
                    log.warn("[Tunnel] 映射文件无法解析，已跳过: {} ({})", file, e.getMessage());
                }
            }
        } catch (IOException e) {
            log.warn("[Tunnel] 读取存储目录失败: {} ({})", dir, e.getMessage());
        }
        return cache.size();
    }

    /**
     * 判断标识是否可安全地用作文件名。
     *
     * @param id 标识
     * @return 合法返回 true
     */
    private static boolean isSafeId(String id) {
        return id != null && SAFE_ID.matcher(id).matches();
    }

    /**
     * 记录对应的文件路径。
     *
     * @param id 标识
     * @return 文件路径
     */
    private Path fileOf(String id) {
        return dir.resolve(id + ".json");
    }

    /**
     * 原子写入单个记录。
     *
     * @param record 记录
     */
    private void write(TunnelRecord record) {
        try {
            Files.createDirectories(dir);
            Path target = fileOf(record.getId());
            Path temp = dir.resolve(record.getId() + ".json.tmp");
            Files.writeString(temp, mapper.writeValueAsString(record), StandardCharsets.UTF_8);
            try {
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            throw new IllegalStateException("写入隧道配置失败: " + e.getMessage(), e);
        }
    }

    /**
     * 仅用于测试/诊断：列出磁盘上的文件名。
     *
     * @return 文件名列表
     */
    public synchronized List<String> listFiles() {
        if (!Files.isDirectory(dir)) {
            return List.of();
        }
        try (Stream<Path> stream = Files.list(dir)) {
            return stream.map(p -> p.getFileName().toString()).sorted().toList();
        } catch (IOException e) {
            return List.of();
        }
    }
}
