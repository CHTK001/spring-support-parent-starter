package com.chua.starter.ai.support.controller;

import com.chua.starter.ai.support.entity.SysAiTavernGroup;
import com.chua.starter.ai.support.entity.SysAiTavernMember;
import com.chua.starter.ai.support.entity.SysAiTavernMessage;
import com.chua.starter.ai.support.service.TavernService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 酒馆（多角色群聊）管理接口。
 *
 * <p>建群者即群主；群成员是拉进来的 AI 角色；按当前登录用户隔离。</p>
 *
 * @author CH
 */
@RestController
@RequestMapping("/v2/tavern")
@RequiredArgsConstructor
public class TavernController {

    private final TavernService tavernService;

    /** 我的群列表（含成员数） */
    /**
     * list Groups
     * @return 返回值
    */
    public List<Map<String, Object>> listGroups() {
        return tavernService.listGroups();
    }

    /** 建群 */
    /**
     * create Group
     * @param Map<String 参数
     * @param body 参数
     * @return 返回值
    */
    public SysAiTavernGroup createGroup(@RequestBody Map<String, String> body) {
        return tavernService.createGroup(body == null ? null : body.get("name"));
    }

    /** 更新群配置 */
    /**
     * update Group
     * @param Map<String 参数
     * @param body 参数
     * @param id 长整数
     * @return 返回值
    */
    public SysAiTavernGroup updateGroup(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        return tavernService.updateGroup(id, body);
    }

    /** 删群 */
    /**
     * delete Group
     * @param id 长整数
     * @return 返回值
    */
    public void deleteGroup(@PathVariable Long id) {
        tavernService.deleteGroup(id);
    }

    /** 群成员（合并角色信息） */
    /**
     * list Members
     * @param id 长整数
     * @return 返回值
    */
    public List<Map<String, Object>> listMembers(@PathVariable Long id) {
        return tavernService.listMembers(id);
    }

    /** 拉角色进群 */
    /**
     * add Member
     * @param Map<String 参数
     * @param body 参数
     * @param id 长整数
     * @return 返回值
    */
    public SysAiTavernMember addMember(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        return tavernService.addMember(id, body);
    }

    /** 移出群成员 */
    /**
     * remove Member
     * @param memberId 长整数
     * @return 返回值
    */
    public void removeMember(@PathVariable Long memberId) {
        tavernService.removeMember(memberId);
    }

    /** 更新成员配置（模型覆盖/随机发言/概率/上下文条数） */
    /**
     * update Member
     * @param Map<String 参数
     * @param body 参数
     * @param memberId 长整数
     * @return 返回值
    */
    public SysAiTavernMember updateMember(@PathVariable Long memberId, @RequestBody Map<String, Object> body) {
        return tavernService.updateMember(memberId, body);
    }

    /** 群消息历史 */
    /**
     * list Messages
     * @param id 长整数
     * @return 返回值
    */
    public List<SysAiTavernMessage> listMessages(@PathVariable Long id) {
        return tavernService.listMessages(id);
    }
}
