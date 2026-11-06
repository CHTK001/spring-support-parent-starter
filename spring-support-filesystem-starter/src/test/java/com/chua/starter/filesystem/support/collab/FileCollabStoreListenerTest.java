package com.chua.starter.filesystem.support.collab;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 协同文档变更广播（SSE 的数据源）。
 *
 * <p>编辑器靠这条流判断「别人存过了」，所以每个写操作都必须广播一次、且带上
 * 变更后的版本号与类型；广播本身不能因为某个连接抛错就中断其余连接。</p>
 *
 * @author Buffy
 * @since 2026/11/06
 */
class FileCollabStoreListenerTest {

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
    void 保存快照会广播带版本号的变更() {
        FileCollabStore store = new FileCollabStore();
        List<FileCollabStore.CollabChange> events = new CopyOnWriteArrayList<>();
        store.addListener(events::add);

        String id = newDoc(store);
        // create 本身也是一次变更
        assertEquals(1, events.size());
        assertEquals("create", events.get(0).kind());
        assertEquals(id, events.get(0).id());

        assertNotNull(store.saveSnapshot(id, "{\"v\":1}", 0));
        assertEquals(2, events.size());
        FileCollabStore.CollabChange saved = events.get(1);
        assertEquals("snapshot", saved.kind());
        assertEquals(1, saved.version(), "广播里必须带变更后的版本号");
        assertTrue(saved.updatedAt() > 0);
    }

    @Test
    void 改权限_重命名_删除各自广播对应的类型() {
        FileCollabStore store = new FileCollabStore();
        List<FileCollabStore.CollabChange> events = new CopyOnWriteArrayList<>();
        String id = newDoc(store);
        store.addListener(events::add);

        assertNotNull(store.updateAccess(id, FileCollabStore.Visibility.PRIVATE, Set.of("u2")));
        assertNotNull(store.rename(id, "b.xlsx"));
        assertTrue(store.delete(id));

        assertEquals(List.of("access", "rename", "delete"),
                events.stream().map(FileCollabStore.CollabChange::kind).toList());
        // 删除事件的版本号用 -1 表示「文档已不存在」
        assertEquals(-1, events.get(2).version());
    }

    @Test
    void 版本冲突时不广播() {
        FileCollabStore store = new FileCollabStore();
        String id = newDoc(store);
        assertNotNull(store.saveSnapshot(id, "{\"v\":1}", 0));

        List<FileCollabStore.CollabChange> events = new CopyOnWriteArrayList<>();
        store.addListener(events::add);

        // 版本已过期：不应写入，也不应广播
        assertEquals(null, store.saveSnapshot(id, "{\"v\":2}", 0));
        assertEquals(0, events.size());
    }

    @Test
    void 移除监听后不再收到广播() {
        FileCollabStore store = new FileCollabStore();
        AtomicInteger count = new AtomicInteger();
        java.util.function.Consumer<FileCollabStore.CollabChange> listener = c -> count.incrementAndGet();
        String id = newDoc(store);
        store.addListener(listener);
        store.saveSnapshot(id, "{\"v\":1}", 0);
        assertEquals(1, count.get());

        store.removeListener(listener);
        store.saveSnapshot(id, "{\"v\":2}", 1);
        assertEquals(1, count.get(), "移除后不应再收到广播");
    }

    @Test
    void 单个监听器抛错不影响其余监听器() {
        FileCollabStore store = new FileCollabStore();
        List<FileCollabStore.CollabChange> events = new CopyOnWriteArrayList<>();
        store.addListener(c -> {
            throw new IllegalStateException("模拟连接已断");
        });
        store.addListener(events::add);

        String id = newDoc(store);
        store.saveSnapshot(id, "{\"v\":1}", 0);

        assertEquals(2, events.size(), "坏连接不能影响其它订阅者：create + snapshot");
    }
}
