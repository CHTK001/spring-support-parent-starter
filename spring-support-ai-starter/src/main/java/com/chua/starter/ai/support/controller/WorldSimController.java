package com.chua.starter.ai.support.controller;

import com.chua.starter.ai.support.entity.SysAiWorld;
import com.chua.starter.ai.support.entity.SysAiWorldAct;
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
    @GetMapping("/worlds")
    public List<Map<String, Object>> listWorlds() {
        return worldSimService.listWorlds();
    }

    /** 世界详情视图（配置 + 场景 + NPC + 关系网 + 最近事件） */
    /**
     * world View
     * @param id 长整数
     * @return 返回值
    */
    @GetMapping("/worlds/{id}/view")
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
    @PostMapping("/worlds")
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
    @PutMapping("/worlds/{id}")
    public SysAiWorld updateWorld(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        return worldSimService.updateWorld(id, body);
    }

    /** 删世界（级联删场景/NPC/关系/事件/消息） */
    /**
     * delete World
     * @param id 长整数
     * @return 返回值
    */
    @DeleteMapping("/worlds/{id}")
    public void deleteWorld(@PathVariable Long id) {
        worldSimService.deleteWorld(id);
    }

    /* ---------------- 场景 ---------------- */

    /**
     * list Scenes
     * @param id 长整数
     * @return 返回值
    */
    @GetMapping("/worlds/{id}/scenes")
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
    @PostMapping("/worlds/{id}/scenes")
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
    @PutMapping("/scenes/{id}")
    public SysAiWorldScene updateScene(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        return worldSimService.updateScene(id, body);
    }

    /** 删场景 */
    /**
     * delete Scene
     * @param id 长整数
     * @return 返回值
    */
    @DeleteMapping("/scenes/{id}")
    public void deleteScene(@PathVariable Long id) {
        worldSimService.deleteScene(id);
    }

    /* ---------------- 世界 NPC ---------------- */

    /**
     * list Npcs
     * @param id 长整数
     * @return 返回值
    */
    @GetMapping("/worlds/{id}/npcs")
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
    @PostMapping("/worlds/{id}/npcs")
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
    @PutMapping("/npcs/{id}")
    public SysAiWorldNpc updateNpc(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        return worldSimService.updateNpc(id, body);
    }

    /** 移除世界 NPC（级联删相关关系） */
    /**
     * remove Npc
     * @param id 长整数
     * @return 返回值
    */
    @DeleteMapping("/npcs/{id}")
    public void removeNpc(@PathVariable Long id) {
        worldSimService.removeNpc(id);
    }

    /** 让 AI 按世界现状自己造角色（body: count / hint） */
    @PostMapping("/worlds/{id}/npcs/ai-create")
    public List<Map<String, Object>> createNpcsByAi(@PathVariable Long id, @RequestBody(required = false) Map<String, Object> body) {
        Map<String, Object> b = body == null ? Map.of() : body;
        Object rawCount = b.get("count");
        Integer count;
        try {
            count = rawCount == null ? null : (int) Math.round(Double.parseDouble(String.valueOf(rawCount)));
        } catch (NumberFormatException e) {
            count = null;
        }
        Object hint = b.get("hint");
        return worldSimService.createNpcsByAi(id, count, hint == null ? null : String.valueOf(hint));
    }

    /* ---------------- 关系 ---------------- */

    /**
     * list Relationships
     * @param id 长整数
     * @return 返回值
    */
    @GetMapping("/worlds/{id}/relationships")
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
    @PutMapping("/relationships/{id}")
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
    @GetMapping("/worlds/{id}/scenes/{sceneId}/messages")
    public List<SysAiWorldMessage> listMessages(@PathVariable Long id, @PathVariable Long sceneId) {
        return worldSimService.listMessages(id, sceneId);
    }

    /* ---------------- 剧本幕次（Act） ---------------- */

    /** 幕次列表（按编号排序，含完成度与剧本落盘标记） */
    @GetMapping("/worlds/{id}/acts")
    public List<Map<String, Object>> listActs(@PathVariable Long id) {
        return worldSimService.listActs(id);
    }

    /** 创建幕次（writing，落盘空剧本文件） */
    @PostMapping("/worlds/{id}/acts")
    public SysAiWorldAct createAct(@PathVariable Long id) {
        return worldSimService.createAct(id);
    }

    /** 幕次详情（元数据 + 剧本 + 素材完成度） */
    @GetMapping("/worlds/{id}/acts/{actNo}")
    public Map<String, Object> actDetail(@PathVariable Long id, @PathVariable Integer actNo) {
        return worldSimService.actDetail(id, actNo);
    }

    /** 推进幕次：writing -> props_ok -> playing -> replay，一步一调 */
    @PostMapping("/worlds/{id}/acts/{actNo}/advance")
    public Map<String, Object> advanceAct(@PathVariable Long id, @PathVariable Integer actNo) {
        return worldSimService.advanceAct(id, actNo);
    }

    /** 重播已演完的幕次（状态回滚到 playing，所见素材保留） */
    @PostMapping("/worlds/{id}/acts/{actNo}/replay")
    public Map<String, Object> replayAct(@PathVariable Long id, @PathVariable Integer actNo) {
        return worldSimService.replayAct(id, actNo);
    }

    /** 结文：replay -> finished，落一条世界事件 */
    @PostMapping("/worlds/{id}/acts/{actNo}/finish")
    public SysAiWorldAct finishAct(@PathVariable Long id, @PathVariable Integer actNo) {
        return worldSimService.finishAct(id, actNo);
    }

    /** 演出正文（只读）：不传 actNo 返回全部已演幕次，传了只看那一幕 */
    @GetMapping("/worlds/{id}/story")
    public List<Map<String, Object>> listStory(@PathVariable Long id,
                                               @RequestParam(required = false) Integer actNo) {
        return worldSimService.listStories(id, actNo);
    }
}