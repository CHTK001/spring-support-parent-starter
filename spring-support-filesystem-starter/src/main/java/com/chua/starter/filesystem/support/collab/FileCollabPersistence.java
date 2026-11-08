package com.chua.starter.filesystem.support.collab;

import com.chua.common.support.task.timer.Timer;
import com.chua.common.support.task.timer.TimerTask;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 协同文档定时落盘。
 *
 * <p><b>为什么是「定时落盘」而不是「每次写就落库」。</b>协同是快照式的：一次保存就是
 * 一整份文档内容（Univer 工作簿 JSON 动辄几百 KB），而编辑器现在是「空闲 3s 自动保存」。
 * 若每次保存都同步落库，就等于用磁盘 IO 给编辑速度设上限，还多出一堆写放大。
 * 这里保持 {@link FileCollabStore} 为唯一读写入口（内存态、零阻塞），
 * 由一个时间轮任务按 {@code plugin.filesystem.collab.persist-seconds} 定期把<b>有变化</b>
 * 的整库刷到磁盘；空闲时因为脏标记为空，一个字节都不写。</p>
 *
 * <p><b>丢数据的窗口</b>：进程被 kill 时，最近一次落盘之后的改动会丢。窗口大小 =
 * 落盘间隔（默认 30s），且下面三条兜底让它不会更差：</p>
 * <ol>
 *   <li>正常关闭（Spring 销毁 Bean）时 {@link #flushNow()} 补一次；</li>
 *   <li>落盘失败会重新标脏，下一轮重试，不会把这次改动吞掉；</li>
 *   <li>写盘是「先写临时文件再原子替换」，中途断电最坏是保留上一份完整文件，
 *       不会出现半截 JSON 导致下次启动整库读不出来。</li>
 * </ol>
 *
 * <p>容忍「磁盘上没有过文件」：首次启动就是空库，不会报错。</p>
 *
 * @author Buffy
 * @since 2026/11/06
 */
@Slf4j
public class FileCollabPersistence {

    /**
     * 落盘文件格式版本（后续字段变更时用于迁移判断）
     */
    private static final int FORMAT_VERSION = 1;

    /**
     * 协同文档存储器（内存态，唯一读写入口）
     */
    private final FileCollabStore store;

    /**
     * 落盘文件路径
     */
    private final Path file;

    /**
     * 落盘间隔（秒）
     */
    private final long intervalSeconds;

    /**
     * 是否启用（关掉时 {@link #start()} 不装载、不调度，等于回到纯内存态）
     */
    private final boolean enabled;

    /**
     * 时间轮（与上传任务同一套 {@link Timer}，不另起线程池）
     */
    private final Timer timer = Timer.newTimer(64, 100, TimeUnit.MILLISECONDS);

    /**
     * 定时落盘句柄（关闭时取消）
     */
    private TimerTask flushTask;

    /**
     * JSON 序列化器：快照里有未知字段时忽略，便于版本前后兼容
     */
    private final ObjectMapper mapper = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    /**
     * 构造。
     *
     * @param store           协同文档存储器
     * @param file            落盘文件路径（父目录不存在会自动创建）
     * @param intervalSeconds 落盘间隔（秒；≤0 时按 30 秒）
     */
    public FileCollabPersistence(FileCollabStore store, Path file, long intervalSeconds) {
        this(store, file, intervalSeconds, true);
    }

    /**
     * 构造。
     *
     * @param store           协同文档存储器
     * @param file            落盘文件路径（父目录不存在会自动创建）
     * @param intervalSeconds 落盘间隔（秒；≤0 时按 30 秒）
     * @param enabled         是否启用落盘
     */
    public FileCollabPersistence(FileCollabStore store, Path file, long intervalSeconds, boolean enabled) {
        this.store = store;
        this.file = file;
        this.intervalSeconds = intervalSeconds > 0 ? intervalSeconds : 30L;
        this.enabled = enabled;
    }

    /**
     * 启动：先装载磁盘内容，再按间隔调度定时落盘。
     *
     * <p>装载失败（文件损坏 / 权限不足）只告警不中断：协同面板仍可用，
     * 只是从空库开始；损坏文件会改名留档，避免被下一轮落盘直接覆盖掉。</p>
     */
    public void start() {
        if (!enabled) {
            log.info("[FileCollab] 定时落盘未启用（plugin.filesystem.collab.persist-enable=false）");
            return;
        }
        load();
        long period = Math.max(1L, intervalSeconds);
        // 首轮延后一个周期再跑：避免刚启动就与装载/预热抢 IO
        this.flushTask = timer.scheduleAtFixedRate(this::flushIfDirty, period, period, TimeUnit.SECONDS);
        log.info("[FileCollab] 定时落盘已启用: file={} interval={}s", file, period);
    }

    /**
     * 停止定时任务并补一次落盘（Bean 销毁时调用）。
     */
    @jakarta.annotation.PreDestroy
    public void shutdown() {
        stop();
        if (enabled) {
            // 正常关闭：把最后一轮改动补上；被 kill -9 时靠间隔内的定时落盘兜底
            flushNow();
        }
    }

    /**
     * 停止定时任务（仅取消调度，不落盘）。
     */
    public void stop() {
        if (flushTask != null) {
            try {
                TimerTask handle = flushTask;
                flushTask = null;
                timer.cancel(handle);
            } catch (Exception e) {
                log.debug("[FileCollab] 取消落盘任务失败（忽略）: {}", e.getMessage());
            }
        }
    }

    /**
     * 从磁盘装载（不存在则视为空库）。
     */
    public void load() {
        if (!Files.exists(file)) {
            log.info("[FileCollab] 未发现落盘文件，按空库启动: {}", file);
            return;
        }
        try {
            String json = Files.readString(file, StandardCharsets.UTF_8);
            if (json.isBlank()) {
                log.warn("[FileCollab] 落盘文件为空，按空库启动: {}", file);
                return;
            }
            StoreFile data = mapper.readValue(json, StoreFile.class);
            store.replaceAll(data.collabs(), data.history(), data.dirs(), data.archived(), data.trashed());
            log.info("[FileCollab] 已从磁盘装载协同文档 {} 份（历史 {} 份文档，目录 {} 个，归档 {}，回收桶 {}）: {}",
                    store.size(), data.history() == null ? 0 : data.history().size(),
                    data.dirs() == null ? 0 : data.dirs().size(),
                    data.archived() == null ? 0 : data.archived().size(),
                    data.trashed() == null ? 0 : data.trashed().size(), file);
        } catch (Exception e) {
            archiveBrokenFile();
            log.error("[FileCollab] 落盘文件解析失败，已按空库启动（原文件改名留档）: {} ({})",
                    file, e.getMessage());
        }
    }

    /**
     * 有改动就落盘（定时任务入口）。
     */
    public void flushIfDirty() {
        if (!store.consumeDirty()) {
            return;
        }
        write();
    }

    /**
     * 立即落盘（关停时调用；无改动时不写）。
     */
    public void flushNow() {
        if (!store.consumeDirty()) {
            return;
        }
        write();
    }

    /**
     * 落盘文件路径（供日志 / 运维核对）。
     *
     * @return 路径
     */
    public Path getFile() {
        return file;
    }

    /**
     * 真正写盘：先写临时文件，再原子替换。
     */
    private void write() {
        Path parent = file.toAbsolutePath().getParent();
        Path tmp = null;
        try {
            if (parent != null) {
                Files.createDirectories(parent);
            }
            StoreFile data = new StoreFile(FORMAT_VERSION, System.currentTimeMillis(),
                    store.exportCollabs(), store.exportHistory(),
                    store.exportDirs(), store.exportArchived(), store.exportTrashed());
            // 快照里可能有几千行 JSON：先落临时文件，避免读到写了一半的内容
            tmp = file.resolveSibling(file.getFileName() + ".tmp");
            Files.writeString(tmp, mapper.writeValueAsString(data), StandardCharsets.UTF_8);
            try {
                Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException atomicUnsupported) {
                // 某些文件系统（网络盘 / 老 Windows）不支持原子移动：退回普通替换
                Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
            }
            tmp = null;
            if (log.isDebugEnabled()) {
                log.debug("[FileCollab] 已落盘 {} 份协同文档: {}", store.size(), file);
            }
        } catch (Exception e) {
            // 关键：写失败要把脏标记放回去，否则这次改动要等到下一次写入才有机会落盘
            store.markDirty();
            log.error("[FileCollab] 落盘失败（已重新标脏，下一轮重试）: {} ({})", file, e.getMessage());
        } finally {
            if (tmp != null) {
                try {
                    Files.deleteIfExists(tmp);
                } catch (IOException ignored) {
                    // 临时文件残留无害：下次写入会覆盖它
                }
            }
        }
    }

    /**
     * 把损坏的落盘文件改名留档（{@code .corrupt-<时间戳>}），避免被下一轮落盘覆盖。
     */
    private void archiveBrokenFile() {
        try {
            Path broken = file.resolveSibling(file.getFileName() + ".corrupt-" + Instant.now().toEpochMilli());
            Files.move(file, broken, StandardCopyOption.REPLACE_EXISTING);
            log.warn("[FileCollab] 损坏文件已留档: {}", broken);
        } catch (IOException e) {
            log.warn("[FileCollab] 损坏文件留档失败（忽略）: {}", e.getMessage());
        }
    }

    /**
     * 落盘文件结构。
     *
     * <p>{@code dirs} / {@code archived} / {@code trashed} 是后加的字段：
     * 老版本文件里没有它们，Jackson 取到 null，装载侧按空处理，因此不需要迁移步骤。</p>
     *
     * @param format   格式版本
     * @param savedAt  落盘时间戳
     * @param collabs  全部协同文档
     * @param history  全部历史快照（id → 条目）
     * @param dirs     显式目录
     * @param archived 归档标记（id → 归档时间戳）
     * @param trashed  回收桶标记（id → 删除时间戳）
     */
    public record StoreFile(int format, long savedAt,
                            List<FileCollabStore.FileCollab> collabs,
                            Map<String, List<FileCollabStore.SnapshotEntry>> history,
                            List<String> dirs,
                            Map<String, Long> archived,
                            Map<String, Long> trashed) {
    }
}
