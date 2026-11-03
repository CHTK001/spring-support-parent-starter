package com.chua.starter.monitor.support.datasync;

import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * 目录监听器：把落在目录里的新文件实时交给同步任务处理。
 *
 * <p>「文件夹同步」如果只靠 cron 轮询，最短也是分钟级，而且每次要重扫整目录；这里用
 * JDK 的 {@link WatchService} 监听 {@code ENTRY_CREATE / ENTRY_MODIFY}，文件一落地就触发。</p>
 *
 * <p>两个必须处理的现实问题：</p>
 * <ul>
 *   <li><b>半成品文件</b>：文件是「边写边到」的，事件到达时内容往往还没写完。直接读会解析失败，
 *       或只同步到前几行。这里对每个候选文件先等 {@link #SETTLE_MILLIS}，再看两次大小是否一致，
 *       稳定了才交给 handler。</li>
 *   <li><b>事件风暴</b>：一次复制会连出很多 ENTRY_MODIFY。用 {@code pending} 集合对同一路径去重，
 *       同一文件同时只会有一个待处理任务。</li>
 * </ul>
 *
 * <p>监听只负责「发现文件并去抖」，具体读文件、映射、写库由调用方（服务实现）完成，
 * 这样监听器可以独立测试，也不把业务依赖带进来。</p>
 *
 * @author CH
 * @since 4.0.0.43
 */
@Slf4j
public class SyncDirWatcher implements AutoCloseable {

    /**
     * 文件落定等待时间（毫秒）：等复制/写入结束后再读
     */
    private static final long SETTLE_MILLIS = 1500L;

    /**
     * 判断「是否还在写」的两次测量间隔（毫秒）
     */
    private static final long STABLE_CHECK_MILLIS = 400L;

    /**
     * 工作线程序号（线程名只用于诊断，不依赖 dir，避免字段初始化顺序问题）
     */
    private static final java.util.concurrent.atomic.AtomicInteger WORKER_SEQ =
            new java.util.concurrent.atomic.AtomicInteger();

    /**
     * 被监听的目录
     */
    private final Path dir;

    /**
     * 源选项（决定匹配规则与是否递归）
     */
    private final SyncSourceOptions options;

    /**
     * 文件落定后的处理回调
     */
    private final Consumer<Path> handler;

    /**
     * 正在等待落定的文件，避免同一文件被反复排队
     */
    private final Set<Path> pending = ConcurrentHashMap.newKeySet();

    /**
     * 工作线程池：单线程足够——同步本来就是串行的，也避免同名文件并发写同一张表
     */
    private final ExecutorService worker = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "sync-dir-worker-" + WORKER_SEQ.incrementAndGet());
        thread.setDaemon(true);
        return thread;
    });

    /**
     * 监听服务与循环线程
     */
    private WatchService watchService;
    private Thread loopThread;

    /**
     * 已关闭标记
     */
    private volatile boolean closed;

    /**
     * 创建目录监听器。
     *
     * @param dir     被监听目录（必须已存在）
     * @param options 源选项
     * @param handler 文件落定后的处理回调
     */
    public SyncDirWatcher(Path dir, SyncSourceOptions options, Consumer<Path> handler) {
        this.dir = dir;
        this.options = options == null ? SyncSourceOptions.defaults() : options.normalized();
        this.handler = handler;
    }

    /**
     * 启动监听。
     *
     * @throws IOException 目录不可监听
     */
    public void start() throws IOException {
        watchService = FileSystems.getDefault().newWatchService();
        register(dir);
        if (options.recursive()) {
            registerRecursively(dir);
        }
// [P3C C1 豁免] 守护/后台线程：有独立生命周期，不适合交给通用执行器
        loopThread = new Thread(this::loop, "sync-dir-watch-" + dir.getFileName());
        loopThread.setDaemon(true);
        loopThread.start();
        // 启动时先补一轮存量：进程停机期间落下的文件不能丢
        worker.submit(() -> enqueueExisting(dir));
        log.info("[sync-dir] 已开始监听目录: {}（匹配 {}，递归={}）", dir, options.pattern(), options.recursive());
    }

    /**
     * 监听循环。
     */
    private void loop() {
        while (!closed) {
            WatchKey key;
            try {
                key = watchService.take();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            } catch (Exception e) {
                if (!closed) {
                    log.warn("[sync-dir] 监听中断, dir={}, message={}", dir, e.getMessage());
                }
                return;
            }
            Path watched = (Path) key.watchable();
            for (WatchEvent<?> event : key.pollEvents()) {
                WatchEvent.Kind<?> kind = event.kind();
                if (kind == StandardWatchEventKinds.OVERFLOW) {
                    // 事件溢出：退回整目录扫描，保证不漏
                    worker.submit(() -> enqueueExisting(dir));
                    continue;
                }
                Path name = (Path) event.context();
                Path child = watched.resolve(name);
                try {
                    if (Files.isDirectory(child)) {
                        if (options.recursive()) {
                            register(child);
                            worker.submit(() -> enqueueExisting(child));
                        }
                        continue;
                    }
                } catch (Exception e) {
                    continue;
                }
                if (kind == StandardWatchEventKinds.ENTRY_CREATE
                        || kind == StandardWatchEventKinds.ENTRY_MODIFY) {
                    submit(child);
                }
            }
            if (!key.reset()) {
                log.debug("[sync-dir] 监听键失效（目录可能已删除）: {}", watched);
                break;
            }
        }
    }

    /**
     * 启动时补扫一遍存量文件。
     *
     * @param root 目录
     */
    private void enqueueExisting(Path root) {
        if (closed) {
            return;
        }
        int depth = options.recursive() ? Integer.MAX_VALUE : 1;
        try (var stream = Files.walk(root, depth)) {
            stream.filter(Files::isRegularFile).forEach(this::submit);
        } catch (Exception e) {
            log.debug("[sync-dir] 补扫失败, dir={}, message={}", root, e.getMessage());
        }
    }

    /**
     * 排队一个候选文件（去重 + 等落定）。
     *
     * @param file 候选文件
     */
    private void submit(Path file) {
        if (closed || !accept(file)) {
            return;
        }
        if (!pending.add(file)) {
            // 已在等待队列里，跳过；大小变化会在落定检查时被识别
            return;
        }
        worker.submit(() -> {
            try {
                if (!settled(file)) {
                    return;
                }
                if (closed || !Files.isRegularFile(file)) {
                    return;
                }
                handler.accept(file);
            } catch (Exception e) {
                log.warn("[sync-dir] 处理文件失败, file={}, message={}", file, e.getMessage());
            } finally {
                pending.remove(file);
            }
        });
    }

    /**
     * 等文件落定：先睡固定时间，再看两次大小是否一致。
     *
     * @param file 文件
     * @return true 表示已稳定
     * @throws Exception 读取属性失败
     */
    private boolean settled(Path file) throws Exception {
        Thread.sleep(SETTLE_MILLIS);
        if (!Files.isRegularFile(file)) {
            return false;
        }
        long first = Files.size(file);
        Thread.sleep(STABLE_CHECK_MILLIS);
        long second = Files.size(file);
        return first == second;
    }

    /**
     * 是否是我们关心的文件：命中匹配规则，且不是编辑器/系统的临时文件。
     *
     * @param file 文件
     * @return true 表示需要同步
     */
    private boolean accept(Path file) {
        String name = file.getFileName().toString();
        if (name.startsWith("~$") || name.startsWith(".") || name.endsWith(".tmp") || name.endsWith(".part")) {
            // Office 锁文件 / 隐藏文件 / 下载中的临时文件
            return false;
        }
        Path doneDir = dir.resolve("done");
        if (file.startsWith(doneDir)) {
            return false;
        }
        return options.matches(file);
    }

    /**
     * 注册一个目录。
     *
     * @param path 目录
     */
    private void register(Path path) {
        try {
            path.register(watchService,
                    StandardWatchEventKinds.ENTRY_CREATE,
                    StandardWatchEventKinds.ENTRY_MODIFY);
        } catch (Exception e) {
            log.warn("[sync-dir] 注册目录失败, dir={}, message={}", path, e.getMessage());
        }
    }

    /**
     * 递归注册所有子目录。
     *
     * @param root 根目录
     */
    private void registerRecursively(Path root) {
        try (var stream = Files.walk(root, Integer.MAX_VALUE)) {
            stream.filter(Files::isDirectory).forEach(this::register);
        } catch (Exception e) {
            log.warn("[sync-dir] 递归注册失败, dir={}, message={}", root, e.getMessage());
        }
    }

    /**
     * 关闭监听并释放线程。
     */
    @Override
    public void close() {
        closed = true;
        if (watchService != null) {
            try {
                watchService.close();
            } catch (Exception e) {
                log.debug("[sync-dir] 关闭监听服务失败: {}", e.getMessage());
            }
        }
        if (loopThread != null) {
            loopThread.interrupt();
        }
        worker.shutdownNow();
        try {
            worker.awaitTermination(2, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        log.info("[sync-dir] 已停止监听目录: {}", dir);
    }

    /**
     * 便于日志与测试的只读视图。
     *
     * @return 目录
     */
    public Path dir() {
        return dir;
    }

    /**
     * 当前待处理文件数（测试用）。
     *
     * @return 待处理数量
     */
    public int pendingCount() {
        return pending.size();
    }

    /**
     * 便于诊断的摘要。
     *
     * @return 摘要
     */
    public Map<String, Object> describe() {
        return Map.of("dir", dir.toString(), "pattern", options.pattern(),
                "recursive", options.recursive(), "pending", pending.size());
    }
}
