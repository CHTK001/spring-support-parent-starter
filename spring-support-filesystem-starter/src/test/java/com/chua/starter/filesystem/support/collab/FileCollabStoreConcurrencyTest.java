package com.chua.starter.filesystem.support.collab;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 协同文档存储的并发语义。
 *
 * <p>这些用例针对的是「读-改-写」竞态：改前 {@code saveSnapshot} 是
 * {@code get → 比对 version → put} 三步，多个并发保存带同一个 {@code expectedVersion}
 * 时会双双通过，用户看到「保存成功」但其中一份内容被覆盖；{@code rename} /
 * {@code updateAccess} 更狠 —— 它们把当时的 snapshot 一起写回，等于把刚存上去的内容
 * 退回上一版。用例会反复跑直到暴露这类问题。</p>
 *
 * @author Buffy
 * @since 2026/11/06
 */
class FileCollabStoreConcurrencyTest {

    /**
     * 每个文档保留的历史条目上限（与 {@code FileCollabStore.MAX_HISTORY} 保持一致）
     */
    private static final int HISTORY_CAP = 50;

    /**
     * 建一份空文档。
     *
     * @param store 存储器
     * @return 文档号
     */
    private String newDoc(FileCollabStore store) {
        return store.create("local", "docs/a.xlsx", "a.xlsx", "u1",
                FileCollabStore.Visibility.PUBLIC, null, "", "excel").id();
    }

    @Test
    void 同一版本的并发保存只能有一个成功() throws Exception {
        int threads = 16;
        FileCollabStore store = new FileCollabStore();
        String id = newDoc(store);

        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger success = new AtomicInteger();
        for (int i = 0; i < threads; i++) {
            final int n = i;
            pool.submit(() -> {
                try {
                    start.await();
                    FileCollabStore.FileCollab r = store.saveSnapshot(id, "{\"n\":" + n + "}", 0);
                    if (r != null) {
                        success.incrementAndGet();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
        }
        start.countDown();
        pool.shutdown();
        assertTrue(pool.awaitTermination(30, TimeUnit.SECONDS), "并发保存应在 30s 内结束");

        assertEquals(1, success.get(), "同一个 expectedVersion 只能有一次写入成功");
        assertEquals(1, store.get(id).version(), "版本号只能 +1 一次");
        List<FileCollabStore.SnapshotEntry> history = store.history(id);
        assertNotNull(history);
        // 第一次保存时原快照为空 → 不压历史
        assertEquals(0, history.size(), "空快照不压历史");
    }

    @Test
    void 并发保存不丢版本_每次成功都对应一个版本() throws Exception {
        int threads = 12;
        FileCollabStore store = new FileCollabStore();
        String id = newDoc(store);
        // 先写入一版，让后续保存都有内容可压历史
        assertNotNull(store.saveSnapshot(id, "{\"seed\":true}", 0));

        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger success = new AtomicInteger();
        for (int i = 0; i < threads; i++) {
            final int n = i;
            pool.submit(() -> {
                try {
                    start.await();
                    // 每个线程都拿「当前版本」重试，模拟客户端读到最新版本后再存
                    for (int attempt = 0; attempt < 50; attempt++) {
                        long current = store.get(id).version();
                        if (store.saveSnapshot(id, "{\"n\":" + n + ",\"a\":" + attempt + "}", current) != null) {
                            success.incrementAndGet();
                            return;
                        }
                    }
                } catch (Exception ignored) {
                    // 单个线程失败不影响断言口径
                }
            });
        }
        start.countDown();
        pool.shutdown();
        assertTrue(pool.awaitTermination(60, TimeUnit.SECONDS), "并发保存应在 60s 内结束");

        // 版本号 = 成功次数 + 1（初始种子那一版），历史条目数 = 种子 + 成功次数 - 1
        FileCollabStore.FileCollab doc = store.get(id);
        assertEquals(success.get() + 1, doc.version(),
                "每个成功的保存都必须体现在版本号上（不能互相覆盖）");
        assertNotNull(doc.snapshot());
        List<FileCollabStore.SnapshotEntry> history = store.history(id);
        assertEquals(success.get(), history.size(),
                "除种子版本外，每次成功保存都应压入一条历史（顺序不能乱、条目不能丢）");
        // 历史版本号必须严格递增（新→旧排列下严格递减）
        for (int i = 1; i < history.size(); i++) {
            assertTrue(history.get(i - 1).version() > history.get(i).version(),
                    "历史版本号应严格递减，实际: " + history.get(i - 1).version() + " → " + history.get(i).version());
        }
    }

    @Test
    void 并发重命名不会把已保存的内容退回上一版() throws Exception {
        FileCollabStore store = new FileCollabStore();
        String id = newDoc(store);
        assertNotNull(store.saveSnapshot(id, "{\"v\":1}", 0));

        ExecutorService pool = Executors.newFixedThreadPool(8);
        CountDownLatch start = new CountDownLatch(1);
        // 一半线程反复保存，一半线程反复重命名；两者都命中同一份文档
        for (int i = 0; i < 8; i++) {
            final int n = i;
            pool.submit(() -> {
                try {
                    start.await();
                    for (int round = 0; round < 200; round++) {
                        if ((n & 1) == 0) {
                            long current = store.get(id).version();
                            store.saveSnapshot(id, "{\"round\":" + round + ",\"by\":" + n + "}", current);
                        } else {
                            store.rename(id, "renamed-" + n + "-" + round + ".xlsx");
                        }
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
        }
        start.countDown();
        pool.shutdown();
        assertTrue(pool.awaitTermination(60, TimeUnit.SECONDS), "并发读写应在 60s 内结束");

        FileCollabStore.FileCollab doc = store.get(id);
        assertNotNull(doc);
        // 关键不变量：重命名只改名字，绝不能把版本号/snapshot 回退
        assertTrue(doc.version() >= 1, "版本号不应被重命名回退: " + doc.version());
        assertNotNull(doc.snapshot(), "快照不应被重命名清空");
        assertTrue(doc.fileName().startsWith("renamed-"), "最后一次重命名应生效: " + doc.fileName());
        // snapshot 与 version 必须自洽：重命名绝不能吞掉已成功的保存。
        // 历史条目数 = min(上限, 版本 - 1)：每保存一次压一条，超出上限丢最旧。
        List<FileCollabStore.SnapshotEntry> history = store.history(id);
        assertEquals(Math.min(HISTORY_CAP, doc.version() - 1), history.size(),
                "历史条目数应等于「min(上限, 当前版本 - 1)」：说明没有哪次保存被静默吞掉");
    }

    @Test
    void 并发改权限不会丢快照() throws Exception {
        FileCollabStore store = new FileCollabStore();
        String id = newDoc(store);
        assertNotNull(store.saveSnapshot(id, "{\"v\":1}", 0));

        ExecutorService pool = Executors.newFixedThreadPool(6);
        CountDownLatch start = new CountDownLatch(1);
        for (int i = 0; i < 6; i++) {
            final int n = i;
            pool.submit(() -> {
                try {
                    start.await();
                    for (int round = 0; round < 100; round++) {
                        if ((n & 1) == 0) {
                            store.saveSnapshot(id, "{\"round\":" + round + "}", store.get(id).version());
                        } else {
                            store.updateAccess(id, FileCollabStore.Visibility.PRIVATE, Set.of("u" + round));
                        }
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
        }
        start.countDown();
        pool.shutdown();
        assertTrue(pool.awaitTermination(60, TimeUnit.SECONDS), "并发读写应在 60s 内结束");

        FileCollabStore.FileCollab doc = store.get(id);
        assertNotNull(doc.snapshot());
        assertEquals(FileCollabStore.Visibility.PRIVATE, doc.visibility());
        assertTrue(!doc.allowedUserIds().isEmpty());
        assertEquals(Math.min(HISTORY_CAP, doc.version() - 1), store.history(id).size(),
                "改权限不应吞掉任何一次已成功的保存");
    }
}
