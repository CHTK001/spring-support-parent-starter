package com.chua.starter.ai.support.service;

import com.chua.starter.ai.support.entity.SysAiWorld;
import com.chua.starter.ai.support.entity.SysAiWorldAct;
import com.chua.starter.ai.support.entity.SysAiWorldEvent;
import com.chua.starter.ai.support.entity.SysAiWorldMessage;
import com.chua.starter.ai.support.entity.SysAiWorldNpc;
import com.chua.starter.ai.support.entity.SysAiWorldRelationship;
import com.chua.starter.ai.support.entity.SysAiWorldScene;

import java.util.List;
import java.util.Map;

/**
 * 模拟（世界沙盒）服务。
 *
 * <p>抽象：世界 → 场景 → 世界 NPC（复用角色库）+ 关系网 + 事件时间线 + 场景对话。
 * 所有数据按当前登录用户隔离；世界视图采用缓存（变更后失效）。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
public interface WorldSimService {

    /* ---------------- 世界 ---------------- */

    /** 当前用户的世界列表（含场景数 / NPC 数 / 最近事件） */
    List<Map<String, Object>> listWorlds();

    /** 世界详情视图（世界配置 + 场景 + NPC + 关系网 + 最近事件），带缓存 */
    Map<String, Object> worldView(Long worldId);

    /** 创建世界（导演模型/演员可空） */
    SysAiWorld createWorld(String name, String description, String era, String directorModel, String actorRoles);

    /** 更新世界配置（名称/世界观/时代/天气/状态） */
    SysAiWorld updateWorld(Long id, Map<String, Object> body);

    /** 删世界（级联删场景/NPC/关系/事件/消息） */
    void deleteWorld(Long id);

    /* ---------------- 场景 ---------------- */

    /** 世界场景列表 */
    List<SysAiWorldScene> listScenes(Long worldId);

    /** 新建场景 */
    SysAiWorldScene createScene(Long worldId, Map<String, Object> body);

    /** 更新场景（名称/描述/排序） */
    SysAiWorldScene updateScene(Long id, Map<String, Object> body);

    /** 删场景（该场景下的世界NPC scene_id 置空） */
    void deleteScene(Long id);

    /* ---------------- 世界 NPC ---------------- */

    /** 世界 NPC 列表（合并角色：名字/头像/系统提示词/模型） */
    List<Map<String, Object>> listNpcs(Long worldId);

    /** 添加角色为世界 NPC（body: roleId/sceneId/nickname/avatar/modelOverride/temperature/persona/isActive） */
    SysAiWorldNpc addNpc(Long worldId, Map<String, Object> body);

    /** 更新世界 NPC（换场景 / 昵称 / 模型 / 温度 / 人设 / 是否参与推进） */
    SysAiWorldNpc updateNpc(Long id, Map<String, Object> body);

    /** 移除世界 NPC（级联删相关关系） */
    void removeNpc(Long id);

    /**
     * 让 AI 按世界现状自己造角色：起名、写人设、布置场景，并补上与现有角色的关系。
     *
     * @param worldId 世界ID
     * @param count   要造几个（1~3，越界按边界取值）
     * @param hint    倾向（可空，观众可给一句“我想要一个……的角色”）
     * @return 新增的世界 NPC 展示行
     */
    List<Map<String, Object>> createNpcsByAi(Long worldId, Integer count, String hint);

    /* ---------------- 关系 ---------------- */

    /** 世界关系网（合并双方名字/头像） */
    List<Map<String, Object>> listRelationships(Long worldId);

    /** 更新关系（好感度/标签/备注） */
    SysAiWorldRelationship updateRelationship(Long id, Map<String, Object> body);

    /** 好感度增减（越界钳制 [-100,100]，无记录则补建），返回最新值 */
    int applyAffinity(Long worldId, Long fromNpcId, Long toNpcId, int delta);

    /* ---------------- 事件时间线 ---------------- */

    /** 事件时间线（按时间倒序，可限定场景与条数） */
    List<SysAiWorldEvent> listEvents(Long worldId, Long sceneId, Integer limit);

    /** 追加一条事件（advance/chat/system） */
    SysAiWorldEvent appendEvent(Long worldId, Long sceneId, String eventType,
                                String roleIds, String title, String content);

    /* ---------------- 场景对话 ---------------- */

    /** 场景对话历史（按时间正序） */
    List<SysAiWorldMessage> listMessages(Long worldId, Long sceneId);

    /** 落库一条场景消息 */
    SysAiWorldMessage saveMessage(SysAiWorldMessage message);

    /** 使世界视图/列表缓存失效（对话、推进等 Service 外写操作后调用） */
    void evictWorldCaches(Long worldId);

    /* ---------------- 剧本模拟（幕次） ---------------- */

    /** 幕次列表（状态 / 阶段 / 完成度 / 剧本落盘标记） */
    List<Map<String, Object>> listActs(Long worldId);

    /** 创建幕次（writing，落盘空剧本文件） */
    SysAiWorldAct createAct(Long worldId);

    /** 幕次详情（元数据 + 剧本 + 素材完成度） */
    Map<String, Object> actDetail(Long worldId, Integer actNo);

    /** 推进幕次：writing -> props_ok -> playing -> replay，一步一调 */
    Map<String, Object> advanceAct(Long worldId, Integer actNo);

    /** 重播已演完的幕次（回滚到 playing） */
    Map<String, Object> replayAct(Long worldId, Integer actNo);

    /** 结文：replay -> finished */
    SysAiWorldAct finishAct(Long worldId, Integer actNo);

    /**
     * 演出正文：只读取出用户侧故事片段（观众看的「幕」），不触发任何生成。
     *
     * <p>actNo 为空时返回该用户在本世界演过的全部幕次，按幕次号升序。
     * 没有演出记录时返回 {@code played=false} 的空壳，前端据此提示「等 AI 演出」。</p>
     *
     * @param worldId 世界
     * @param actNo   幕次号，可空
     * @return 演出正文列表
     */
    List<Map<String, Object>> listStories(Long worldId, Integer actNo);

    /** 使幕次维度的世界视图/列表缓存失效 */
    void evictActCaches(Long worldId);
}