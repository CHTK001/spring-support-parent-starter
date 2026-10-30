package com.chua.starter.ai.support.service.act;

import java.nio.file.Path;
import java.util.List;

/**
 * 剧本模拟的落盘/取盘服务。
 *
 * <p>幕次/片段/过场全部写服务器目录，结构：
 *   <pre>
 *     {story_root}/{worldId}/acts/act{N}.json     幕次剧本（共享，按世界隔离）
 *     {story_root}/{worldId}/stories/{userId}/... 用户侧故事片段（按账号隔离）
 *     {story_root}/{worldId}/media/               生成资源目录（发图/发视频任务产物）
 *   </pre>
 *
 * <p>目录名来自设置分组 {@code sim.story_base_path}（默认 {@code worlds}，相对进程工作目录），
 * 写文件前自动建世界目录（参考 {@code GenerateService} 的 {@code media_base_path} 策略）。</p>
 *
 * @author CH
 * @since 4.0.0.51
 */
public interface StoryStoreService {

    /** 剧本模拟数据根目录（相对路径，如 "worlds"） */
    String storyRoot();

    /** 根目录（绝对路径） */
    Path root();

    /** 世界目录：{root}/{worldId} */
    Path worldDir(Long worldId);

    /** 幕次目录：{root}/{worldId}/acts */
    Path actDir(Long worldId);

    /** 用户故事目录：{root}/{worldId}/stories/{userId} */
    Path storyDir(Long worldId, String userId);

    /** 生成/构建世界目录（mkdir） */
    void ensureWorldDir(Long worldId);

    /** 写剧本文件（覆盖写） */
    boolean writeAct(Long worldId, Integer actNo, String json);

    /** 读剧本文件 */
    String readAct(Long worldId, Integer actNo);

    /** 删剧本文件（幂等） */
    boolean deleteAct(Long worldId, Integer actNo);

    /** 列出该世界已存在的幕次编号（升序） */
    List<Integer> actNumbers(Long worldId);

    /** 写用户故事片段 */
    boolean writeStory(Long worldId, String userId, Integer actNo, String json);

    /** 读用户故事片段 */
    String readStory(Long worldId, String userId, Integer actNo);

    /** 删用户故事片段 */
    boolean deleteStory(Long worldId, String userId, Integer actNo);

    /** 列出该用户已看过的幕次（升序） */
    List<Integer> storyActNumbers(Long worldId, String userId);
}
