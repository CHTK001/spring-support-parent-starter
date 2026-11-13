package com.chua.starter.filesystem.support.collab;

// [P3C A20 豁免] A20/A30/A31: 含日志/界面/SQL 字符串字面量等非业务魔法值场景，豁免。
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * hub 房间落盘文件的删除语义。
 *
 * <p>盯住一个真实缺陷：文档被彻底删除时调用 {@link FileCollabHub#reset(String)}，
 * 如果此时房间已经空闲卸载（进程重启后没人连过它，或者最后一个连接离开后卸载），
 * 早期实现只删内存里的房间，磁盘上的 {@code <hub-dir>/<sha256(id)>.json} 会永久残留，
 * 越积越多且永远清不掉。</p>
 *
 * @author Buffy
 * @since 2026/11/09
 */
class FileCollabHubResetTest {

    /**
     * 与 hub 落盘规则一致的文件名：sha256(房间号) 前 32 位。
     *
     * @param id 房间号
     * @return 落盘文件名
     */
    private static String fileName(String id) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256")
                .digest(id.getBytes(StandardCharsets.UTF_8));
        StringBuilder hex = new StringBuilder();
        for (byte b : digest) {
            hex.append(String.format("%02x", b));
        }
        return hex.substring(0, 32) + ".json";
    }

    /**
     * 造一个已落盘的房间（不经过连接，直接注册快照触发同步写盘）。
     *
     * @param hub  hub
     * @param id   房间号
     */
    private static void seedRoom(FileCollabHub hub, String id) {
        assertTrue(hub.registerSnapshot(id, 1L, Collections.emptyList()));
    }

    @Test
    // [P3C NM-命名 豁免]
    void 未装载的房间也要删掉落盘文件(@TempDir Path dir) throws Exception {
        FileCollabHub writer = new FileCollabHub(dir);
        seedRoom(writer, "doc-not-loaded");
        Path file = dir.resolve(fileName("doc-not-loaded"));
        assertTrue(Files.exists(file), "前置条件：房间文件必须已经落盘");

        // 换一个实例 = 进程重启后内存里没有这个房间，只剩磁盘文件
        FileCollabHub fresh = new FileCollabHub(dir);
        assertFalse(fresh.loaded("doc-not-loaded"), "前置条件：新实例还没装载该房间");

        fresh.reset("doc-not-loaded");
        assertFalse(Files.exists(file), "彻底删除文档后，未装载房间的落盘文件也必须删掉");
    }

    @Test
    // [P3C NM-命名 豁免]
    void 已装载房间reset后文件消失且状态清零(@TempDir Path dir) throws Exception {
        FileCollabHub hub = new FileCollabHub(dir);
        seedRoom(hub, "doc-loaded");
        Path file = dir.resolve(fileName("doc-loaded"));
        assertTrue(Files.exists(file));
        assertTrue(hub.loaded("doc-loaded"));

        hub.reset("doc-loaded");
        assertFalse(Files.exists(file), "内存中的房间 reset 也要删文件");
        assertFalse(hub.loaded("doc-loaded"), "reset 后内存房间必须摘掉");

        Map<String, Object> state = hub.state("doc-loaded");
        assertEquals(0L, ((Number) state.get("seq")).longValue(), "重新装载应是空房间");
        assertEquals(0L, ((Number) state.get("snapshotSeq")).longValue());
    }

    @Test
    // [P3C NM-命名 豁免]
    void reset只影响目标房间(@TempDir Path dir) throws Exception {
        FileCollabHub hub = new FileCollabHub(dir);
        seedRoom(hub, "doc-keep");
        seedRoom(hub, "doc-drop");
        Path keep = dir.resolve(fileName("doc-keep"));
        Path drop = dir.resolve(fileName("doc-drop"));

        hub.reset("doc-drop");
        assertFalse(Files.exists(drop));
        assertTrue(Files.exists(keep), "其他房间的落盘文件不能被误删");
        assertTrue(hub.loaded("doc-keep"));
    }

    @Test
    // [P3C NM-命名 豁免]
    void 重复reset不抛错且保持文件缺失(@TempDir Path dir) throws Exception {
        FileCollabHub hub = new FileCollabHub(dir);
        seedRoom(hub, "doc-twice");
        Path file = dir.resolve(fileName("doc-twice"));

        hub.reset("doc-twice");
        hub.reset("doc-twice");
        assertFalse(Files.exists(file));
        List<String> left = Files.exists(dir)
                ? Files.list(dir).map(p -> p.getFileName().toString()).toList()
                : List.of();
        assertFalse(left.contains(fileName("doc-twice")));
    }
}
