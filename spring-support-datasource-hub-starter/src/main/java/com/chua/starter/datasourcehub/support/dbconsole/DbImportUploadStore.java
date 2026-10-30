package com.chua.starter.datasourcehub.support.dbconsole;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 导入文件暂存区：上传一次，后续各步复用同一个令牌。
 *
 * <p>导入向导有 7 步，其中「选文件 → 附加选项 → 目标表 → 字段映射」都会重新解析同一个文件
 * （用户每改一次分隔符/行号就要重算预览）。如果每步都重传，几十 MB 的 Excel 会被反复
 * 读进内存并跨公网上传，交互上也不可接受 —— 用户看到的会是「点一下下一步转 20 秒」。</p>
 *
 * <p>因此第 2 步上传一次，服务端落到临时目录并返回 {@code token}；后续步骤只传 token 与选项。
 * 落盘而不是放内存，是因为大文件（几百 MB CSV）放内存会直接把堆打满。</p>
 *
 * <p><b>过期与清理</b>：{@value #TTL_MINUTES} 分钟未被访问即视为放弃，下次访问或条目数超限时
 * 顺带清理。只用一次性令牌 + 文件名不含用户输入，落盘路径不可能被穿越。</p>
 *
 * @author CH
 * @since 4.0.0.45
 */
public final class DbImportUploadStore {

    /** 暂存有效期（分钟）：够走完 7 步，又不至于把磁盘占满 */
    private static final long TTL_MINUTES = 60;

    /** 同时保留的上传条目上限，超限先清过期再按最旧淘汰 */
    private static final int MAX_ENTRIES = 32;

    /** 单文件大小上限（字节）：与前端提示一致 */
    public static final long MAX_FILE_BYTES = 512L * 1024 * 1024;

    private static final SecureRandom RANDOM = new SecureRandom();

    /** token → 条目 */
    private static final Map<String, Entry> ENTRIES = new ConcurrentHashMap<>();

    private DbImportUploadStore() {
    }

    /**
     * 一条暂存记录。
     *
     * @param token    令牌
     * @param path     临时文件路径
     * @param fileName 原始文件名
     * @param size     字节数
     * @param createdAt 创建时间
     */
    public record Entry(String token, Path path, String fileName, long size, Instant createdAt) {

        /**
         * 是否已过期。
         *
         * @return true=过期
         */
        public boolean expired() {
            return createdAt.plus(Duration.ofMinutes(TTL_MINUTES)).isBefore(Instant.now());
        }
    }

    /**
     * 保存一个上传文件。
     *
     * @param fileName 原始文件名（只用于回显与后缀探测，不参与路径拼接）
     * @param bytes    文件字节
     * @return 暂存条目
     * @throws IOException 写临时文件失败
     */
    public static Entry save(String fileName, byte[] bytes) throws IOException {
        if (bytes == null || bytes.length == 0) {
            throw new IOException("文件内容为空");
        }
        if (bytes.length > MAX_FILE_BYTES) {
            throw new IOException("文件过大（上限 " + (MAX_FILE_BYTES / 1024 / 1024) + "MB）");
        }
        evictIfNeeded();
        String token = newToken();
        Path path = Files.createTempFile("dbimport-", ".dat");
        Files.write(path, bytes);
        Entry entry = new Entry(token, path, fileName, bytes.length, Instant.now());
        ENTRIES.put(token, entry);
        return entry;
    }

    /**
     * 按令牌取条目；过期条目会被删除并返回 null。
     *
     * @param token 令牌
     * @return 条目；不存在或已过期返回 null
     */
    public static Entry get(String token) {
        if (token == null || token.isBlank()) {
            return null;
        }
        Entry entry = ENTRIES.get(token);
        if (entry == null) {
            return null;
        }
        if (entry.expired()) {
            discard(token);
            return null;
        }
        return entry;
    }

    /**
     * 读取条目内容。
     *
     * @param token 令牌
     * @return 字节
     * @throws IOException 条目不存在或读失败
     */
    public static byte[] read(String token) throws IOException {
        Entry entry = get(token);
        if (entry == null) {
            throw new IOException("上传文件已过期，请重新选择文件");
        }
        return Files.readAllBytes(entry.path());
    }

    /**
     * 丢弃一个条目（读完后清理，避免临时目录堆积）。
     *
     * @param token 令牌
     */
    public static void discard(String token) {
        Entry entry = ENTRIES.remove(token);
        if (entry != null) {
            try {
                Files.deleteIfExists(entry.path());
            } catch (IOException ignored) {
                // 删不掉就留给操作系统临时目录清理
            }
        }
    }

    /**
     * evict If Needed
     * @return 返回值
    */
    private static void evictIfNeeded() {
        ENTRIES.values().removeIf(entry -> {
            if (entry.expired()) {
                try {
                    Files.deleteIfExists(entry.path());
                } catch (IOException ignored) {
                    // 同上
                }
                return true;
            }
            return false;
        });
        if (ENTRIES.size() < MAX_ENTRIES) {
            return;
        }
        // 还超限就淘汰最旧的一条：用户此时早已切换到别的文件
        ENTRIES.values().stream()
                .min((a, b) -> a.createdAt().compareTo(b.createdAt()))
                .map(Entry::token)
                .ifPresent(DbImportUploadStore::discard);
    }

    /**
     * new Token
     * @return 返回值
    */
    private static String newToken() {
        byte[] bytes = new byte[16];
        RANDOM.nextBytes(bytes);
        StringBuilder sb = new StringBuilder(32);
        for (byte b : bytes) {
            sb.append(Character.forDigit((b >> 4) & 0xF, 16));
            sb.append(Character.forDigit(b & 0xF, 16));
        }
        return sb.toString();
    }
}
