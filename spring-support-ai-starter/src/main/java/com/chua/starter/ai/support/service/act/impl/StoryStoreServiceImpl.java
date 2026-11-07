package com.chua.starter.ai.support.service.act.impl;

import com.chua.starter.ai.support.configuration.client.GlobalSettingReader;
import com.chua.starter.ai.support.service.act.StoryStoreService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;

/**
 * 剧本模拟的落盘/取盘实现。
 *
 * <p>幕次写 {root}/{worldId}/acts/act{N}.json，用户故事片段写
 * {root}/{worldId}/stories/{userId}/act{N}.json。目录不存在时自动创建（按设置开关
 * {@code sim.story_mkdir} 控制）。</p>
 *
 * @author CH
 * @since 4.0.0.51
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StoryStoreServiceImpl implements StoryStoreService {

    /** 剧本模拟数据根目录（相对进程工作目录） */
    private static final String FIELD_STORY_BASE_PATH = "story_base_path";

    /** "根目录不存在时自动创建" 开关，按设置页的布尔开关走 */
    private static final String FIELD_STORY_MKDIR = "story_mkdir";

    /** 幕次文件的唯一前置命名 */
    private static final String ACT_NAME_PREFIX = "act";

    /** 幕次文件全名 */
    private static final String ACT_NAME_SUFFIX = ".json";

    /** 设置分组 */
    private static final String SETTING_GROUP = "sim";

    /** 设置读取器（取 config 值） */
    private final GlobalSettingReader globalSettingReader;

    @Override
    public String storyRoot() {
        String root = globalSettingReader.getStr(SETTING_GROUP, FIELD_STORY_BASE_PATH);
        return root == null || root.isBlank() ? "worlds" : root;
    }

    @Override
    public Path root() {
        return Paths.get(storyRoot());
    }

    @Override
    public Path worldDir(Long worldId) {
        return root().resolve(String.valueOf(worldId));
    }

    @Override
    public Path actDir(Long worldId) {
        return worldDir(worldId).resolve("acts");
    }

    @Override
    public Path storyDir(Long worldId, String userId) {
        return worldDir(worldId).resolve("stories").resolve(userId);
    }

    @Override
    public void ensureWorldDir(Long worldId) {
        if (!Boolean.TRUE.equals(globalSettingReader.getBool(SETTING_GROUP, FIELD_STORY_MKDIR, true))) {
            return;
        }
        Path dir = worldDir(worldId);
        tryEnsure(dir);
    }

    /* ---------------- 幕次 ---------------- */

    @Override
    public boolean writeAct(Long worldId, Integer actNo, String json) {
        Path path = filePath(actNo, actDir(worldId));
        return writeFile(path, json);
    }

    @Override
    public String readAct(Long worldId, Integer actNo) {
        Path path = filePath(actNo, actDir(worldId));
        return readFile(path);
    }

    @Override
    public boolean deleteAct(Long worldId, Integer actNo) {
        Path path = filePath(actNo, actDir(worldId));
        try {
            return Files.deleteIfExists(path);
        } catch (IOException e) {
            log.warn("[StoryStore] 删剧本文件失败 path={}: {}", path, e.getMessage());
            return false;
        }
    }

    @Override
    public List<Integer> actNumbers(Long worldId) {
        Path dir = actDir(worldId);
        if (!Files.isDirectory(dir)) {
            return Collections.emptyList();
        }
        List<Integer> out = new ArrayList<>();
        try (Stream<Path> s = Files.list(dir)) {
            for (Path p : s.toList()) {
                int no = actNumberOf(p.getFileName().toString());
                if (no > 0) out.add(no);
            }
        } catch (Exception e) {
            log.debug("[StoryStore] 列举幕次失败 worldId={}: {}", worldId, e.getMessage());
            return Collections.emptyList();
        }
        out.sort(Integer::compareTo);
        return out;
    }

    /* ---------------- 用户故事 ---------------- */

    @Override
    public boolean writeStory(Long worldId, String userId, Integer actNo, String json) {
        Path path = filePath(actNo, storyDir(worldId, userId));
        return writeFile(path, json);
    }

    @Override
    public String readStory(Long worldId, String userId, Integer actNo) {
        Path path = filePath(actNo, storyDir(worldId, userId));
        return readFile(path);
    }

    @Override
    public boolean deleteStory(Long worldId, String userId, Integer actNo) {
        Path path = filePath(actNo, storyDir(worldId, userId));
        try {
            return Files.deleteIfExists(path);
        } catch (IOException e) {
            log.warn("[StoryStore] 删用户故事失败 path={}: {}", path, e.getMessage());
            return false;
        }
    }

    @Override
    public List<Integer> storyActNumbers(Long worldId, String userId) {
        Path dir = storyDir(worldId, userId);
        if (!Files.isDirectory(dir)) {
            return Collections.emptyList();
        }
        List<Integer> out = new ArrayList<>();
        try (Stream<Path> s = Files.list(dir)) {
            for (Path p : s.toList()) {
                int no = actNumberOf(p.getFileName().toString());
                if (no > 0) out.add(no);
            }
        } catch (Exception e) {
            log.debug("[StoryStore] 列举用户故事失败 worldId={}: {}", worldId, e.getMessage());
            return Collections.emptyList();
        }
        out.sort(Integer::compareTo);
        return out;
    }

    /* ---------------- 私有辅助 ---------------- */

    private Path filePath(Integer actNo, Path dir) {
        return dir.resolve(ACT_NAME_PREFIX + actNo + ACT_NAME_SUFFIX);
    }

    /** writeFile 方法 */
    private boolean writeFile(Path path, String json) {
        try {
            tryEnsure(path.getParent());
            Files.write(path, json.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return true;
        } catch (Exception e) {
            log.error("[StoryStore] 写文件失败 path={}: {}", path, e.getMessage());
            return false;
        }
    }

    /** readFile 方法 */
    private String readFile(Path path) {
        try {
            if (!Files.exists(path)) {
                return null;
            }
            return new String(Files.readAllBytes(path), java.nio.charset.StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.error("[StoryStore] 读文件失败 path={}: {}", path, e.getMessage());
            return null;
        }
    }

    /** tryEnsure 方法 */
    private void tryEnsure(Path dir) {
        if (dir == null || Files.isDirectory(dir)) {
            return;
        }
        try {
            Files.createDirectories(dir);
        } catch (Exception e) {
            log.warn("[StoryStore] 目录创建失败 dir={}: {}", dir, e.getMessage());
        }
    }

    /**
     * 从文件名解析幕次编号（如 "act3.json" → 3）
     *
     * @param f 文件名
     * @return 幕次编号
     */
    private int actNumberOf(String f) {
        try {
            return Integer.parseInt(f.substring(ACT_NAME_PREFIX.length(), f.length() - ACT_NAME_SUFFIX.length()));
        } catch (Exception e) {
            return 0;
        }
    }
}
