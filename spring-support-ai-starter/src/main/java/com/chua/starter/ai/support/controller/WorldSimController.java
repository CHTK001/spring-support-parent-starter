package com.chua.starter.ai.support.controller;

import com.chua.starter.ai.support.entity.SysAiWorld;
import com.chua.starter.ai.support.entity.SysAiWorldEvent;
import com.chua.starter.ai.support.entity.SysAiWorldMessage;
import com.chua.starter.ai.support.entity.SysAiWorldNpc;
import com.chua.starter.ai.support.entity.SysAiWorldRelationship;
import com.chua.starter.ai.support.entity.SysAiWorldScene;
import com.chua.starter.ai.support.service.WorldSimService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 模拟（世界沙盒）管理接口。
 *
 * <p>世界 → 场景 → 世界 NPC（复用角色库）→ 关系网 → 事件时间线 → 场景对话。
 * 按当前登录用户隔离；流式对话与时间推进在 {@link WorldSimChatController}。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@RestController
@RequestMapping("/v2/world-sim")
@RequiredArgsConstructor
public class WorldSimController {

    private final WorldSimService worldSimService;

    /* ---------------- 世界 ---------------- */

    /**
     * list Worlds
     * @return 返回值
    */
    public List<Map<String, Object>> listWorlds() {
        return worldSimService.listWorlds();
    }

    /** 世界详情视图（配置 + 场景 + NPC + 关系网 + 最近事件） */
    /**
     * world View
     * @param id 长整数
     * @return 返回值
    */
    public Map<String, Object> worldView(@PathVariable Long id) {
        return worldSimService.worldView(id);
    }

    /** 建世界（自动附送「起始之地」场景） */
    /**
     * create World
     * @param Map<String 参数
     * @param body 参数
     * @return 返回值
    */
    public SysAiWorld createWorld(@RequestBody Map<String, String> body) {
        return worldSimService.createWorld(
                body == null ? null : body.get("name"),
                body == null ? null : body.get("description"),
                body == null ? null : body.get("era"));
    }

    /** 更新世界配置 */
    /**
     * update World
     * @param Map<String 参数
     * @param body 参数
     * @param id 长整数
     * @return 返回值
    */
    public SysAiWorld updateWorld(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        return worldSimService.updateWorld(id, body);
    }

    /** 删世界（级联删场景/NPC/关系/事件/消息） */
    /**
     * delete World
     * @param id 长整数
     * @return 返回值
    */
    public void deleteWorld(@PathVariable Long id) {
        worldSimService.deleteWorld(id);
    }

    /* ---------------- 场景 ---------------- */

    /**
     * list Scenes
     * @param id 长整数
     * @return 返回值
    */
    public List<SysAiWorldScene> listScenes(@PathVariable Long id) {
        return worldSimService.listScenes(id);
    }

    /** 新建场景 */
    /**
     * create Scene
     * @param Map<String 参数
     * @param body 参数
     * @param id 长整数
     * @return 返回值
    */
    public SysAiWorldScene createScene(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        return worldSimService.createScene(id, body);
    }

    /** 更新场景 */
    /**
     * update Scene
     * @param Map<String 参数
     * @param body 参数
     * @param id 长整数
     * @return 返回值
    */
    public SysAiWorldScene updateScene(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        return worldSimService.updateScene(id, body);
    }

    /** 删场景 */
    /**
     * delete Scene
     * @param id 长整数
     * @return 返回值
    */
    public void deleteScene(@PathVariable Long id) {
        worldSimService.deleteScene(id);
    }

    /* ---------------- 世界 NPC ---------------- */

    /**
     * list Npcs
     * @param id 长整数
     * @return 返回值
    */
    public List<Map<String, Object>> listNpcs(@PathVariable Long id) {
        return worldSimService.listNpcs(id);
    }

    /** 添加角色为世界 NPC */
    /**
     * add Npc
     * @param Map<String 参数
     * @param body 参数
     * @param id 长整数
     * @return 返回值
    */
    public SysAiWorldNpc addNpc(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        return worldSimService.addNpc(id, body);
    }

    /** 更新世界 NPC */
    /**
     * update Npc
     * @param Map<String 参数
     * @param body 参数
     * @param id 长整数
     * @return 返回值
    */
    public SysAiWorldNpc updateNpc(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        return worldSimService.updateNpc(id, body);
    }

    /** 移除世界 NPC（级联删相关关系） */
    /**
     * remove Npc
     * @param id 长整数
     * @return 返回值
    */
    public void removeNpc(@PathVariable Long id) {
        worldSimService.removeNpc(id);
    }

    /* ---------------- 关系 ---------------- */

    /**
     * list Relationships
     * @param id 长整数
     * @return 返回值
    */
    public List<Map<String, Object>> listRelationships(@PathVariable Long id) {
        return worldSimService.listRelationships(id);
    }

    /** 更新关系（好感度/标签/备注） */
    /**
     * update Relationship
     * @param Map<String 参数
     * @param body 参数
     * @param id 长整数
     * @return 返回值
    */
    public SysAiWorldRelationship updateRelationship(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        return worldSimService.updateRelationship(id, body);
    }

    /* ---------------- 事件时间线 ---------------- */

    /** 事件时间线（倒序，可限定场景与条数） */
    @GetMapping("/worlds/{id}/events")
    public List<SysAiWorldEvent> listEvents(@PathVariable Long id,
                                            @RequestParam(required = false) Long sceneId,
                                            @RequestParam(required = false) Integer limit) {
        return worldSimService.listEvents(id, sceneId, limit);
    }

    /* ---------------- 场景对话 ---------------- */

    /**
     * list Messages
     * @param id 长整数
     * @param sceneId 长整数
     * @return 返回值
    */
    public List<SysAiWorldMessage> listMessages(@PathVariable Long id, @PathVariable Long sceneId) {
        return worldSimService.listMessages(id, sceneId);
    }
}