package com.chua.starter.filesystem.support.collab;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 协同文档定时落盘的往返测试。
 *
 * <p>盯四件事：①落盘 → 装载后内容/版本/权限/历史都对得上；②空闲不写盘（没有改动就不落）；
 * ③写失败不会把改动吞掉（脏标记要放回去）；④落盘文件损坏时按空库启动且原文件留档。</p>
 *
 * @author Buffy
 * @since 2026/11/06
 */
class FileCollabPersistenceTest {

    /**
     * 造一份协同文档（不落盘，只进内存）。
     *
     * @param store 存储器
     * @return 文档
     */
    private FileCollabStore.FileCollab seed(FileCollabStore store) {
        Set<String> users = new LinkedHashSet<>();
        users.add("u2");
        FileCollabStore.FileCollab c = store.create("local", "docs/a.xlsx", "a.xlsx", "u1",
                FileCollabStore.Visibility.PRIVATE, users, "team/x", "excel");
        // 保存两次：顺带产出一条历史快照
        FileCollabStore.FileCollab v1 = store.saveSnapshot(c.id(), "{\"v\":1}", 0);
        assertNotNull(v1);
        FileCollabStore.FileCollab v2 = store.saveSnapshot(c.id(), "{\"v\":2}", 1);
        assertNotNull(v2);
        return v2;
    }

    @Test
    void 落盘后装载_内容版本权限历史都要一致(@TempDir Path dir) {
        Path file = dir.resolve("collab-store.json");
        FileCollabStore store = new FileCollabStore();
        FileCollabStore.FileCollab saved = seed(store);
        // 再放一份公开文档，覆盖两种可见性
        FileCollabStore.FileCollab pub = store.create("local", "docs/b.txt", "b.txt", "u1",
                FileCollabStore.Visibility.PUBLIC, null, "", "text");
        store.saveSnapshot(pub.id(), "hello", 0);

        FileCollabPersistence writer = new FileCollabPersistence(store, file, 30);
        writer.flushNow();
        assertTrue(Files.exists(file), "落盘文件必须存在");

        FileCollabStore loaded = new FileCollabStore();
        new FileCollabPersistence(loaded, file, 30).load();

        assertEquals(2, loaded.size());
        FileCollabStore.FileCollab back = loaded.get(saved.id());
        assertNotNull(back);
        assertEquals("a.xlsx", back.fileName());
        assertEquals("local", back.bucket());
        assertEquals("docs/a.xlsx", back.objectKey());
        assertEquals("u1", back.owner());
        assertEquals(FileCollabStore.Visibility.PRIVATE, back.visibility());
        assertEquals(Set.of("u2"), back.allowedUserIds());
        assertEquals("team/x", back.path());
        assertEquals("excel", back.type());
        assertEquals(2, back.version(), "版本号要跟着回来");
        assertEquals("{\"v\":2}", back.snapshot(), "快照内容要跟着回来");

        List<FileCollabStore.SnapshotEntry> history = loaded.history(saved.id());
        assertNotNull(history);
        assertEquals(1, history.size(), "每次保存压一条历史：两次保存 = 一条历史（v1）");
        assertEquals(1, history.get(0).version());
        assertEquals("{\"v\":1}", history.get(0).snapshot());
        assertEquals("hello", loaded.get(pub.id()).snapshot());
    }

    @Test
    void 没有改动时不写盘(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("collab-store.json");
        FileCollabStore store = new FileCollabStore();
        seed(store);
        FileCollabPersistence persistence = new FileCollabPersistence(store, file, 30);

        persistence.flushNow();
        long firstWrite = Files.getLastModifiedTime(file).toMillis();

        // 期间没有任何写入 → 再刷两次都不该碰文件
        Thread.sleep(20);
        persistence.flushIfDirty();
        persistence.flushNow();
        assertEquals(firstWrite, Files.getLastModifiedTime(file).toMillis(),
                "没有改动时不应重写落盘文件");
    }

    @Test
    void 装载后不应把自己再标脏(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("collab-store.json");
        FileCollabStore store = new FileCollabStore();
        seed(store);
        new FileCollabPersistence(store, file, 30).flushNow();

        FileCollabStore loaded = new FileCollabStore();
        new FileCollabPersistence(loaded, file, 30).load();
        assertFalse(loaded.consumeDirty(), "装载后应与磁盘一致，脏标记必须是空的");
    }

    @Test
    void 写失败会把脏标记放回去(@TempDir Path dir) throws Exception {
        // 用一个「父路径是文件」的路径制造写失败（createDirectories 会抛错）
        Path blocker = dir.resolve("blocker");
        Files.writeString(blocker, "x");
        Path file = blocker.resolve("collab-store.json");

        FileCollabStore store = new FileCollabStore();
        seed(store);
        FileCollabPersistence persistence = new FileCollabPersistence(store, file, 30);

        persistence.flushNow();
        assertFalse(Files.exists(file));
        assertTrue(store.consumeDirty(), "落盘失败必须重新标脏，否则这次改动永远不会落盘");
    }

    @Test
    void 落盘文件损坏时按空库启动且原文件留档(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("collab-store.json");
        Files.writeString(file, "{ 这不是 JSON", StandardCharsets.UTF_8);

        FileCollabStore store = new FileCollabStore();
        new FileCollabPersistence(store, file, 30).load();

        assertEquals(0, store.size());
        boolean archived = Files.list(dir)
                .anyMatch(p -> p.getFileName().toString().startsWith("collab-store.json.corrupt-"));
        assertTrue(archived, "损坏文件应改名留档，不能被下一轮落盘覆盖掉");
    }

    @Test
    void 关闭开关时既不装载也不调度(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("collab-store.json");
        FileCollabStore store = new FileCollabStore();
        seed(store);
        new FileCollabPersistence(store, file, 30).flushNow();

        FileCollabStore other = new FileCollabStore();
        FileCollabPersistence disabled = new FileCollabPersistence(other, file, 1, false);
        disabled.start();
        assertEquals(0, other.size(), "关掉落盘时不应装载磁盘内容");
        disabled.shutdown();
        assertFalse(Files.exists(dir.resolve("collab-store.json.tmp")));
    }
}
