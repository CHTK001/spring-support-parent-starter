package com.chua.starter.ai.support.service;

import com.chua.starter.ai.support.entity.SysAiTavernGroup;
import com.chua.starter.ai.support.entity.SysAiTavernMember;
import com.chua.starter.ai.support.entity.SysAiTavernMessage;

import java.util.List;
import java.util.Map;

/**
 * 酒馆（多角色群聊）服务。
 *
 * <p>建群者即群主（真人），群成员是 AI 角色。所有数据按当前登录用户隔离。</p>
 *
 * @author CH
 */
public interface TavernService {

    /** 当前用户的群列表（附带成员数） */
    List<Map<String, Object>> listGroups();

    /** 建群（当前登录用户即群主） */
    SysAiTavernGroup createGroup(String name);

    /** 更新群配置（名称 / 默认回复成员 / 自动路由 / 随机概率 / 上下文条数） */
    SysAiTavernGroup updateGroup(Long id, Map<String, Object> body);

    /** 删群（级联删成员与消息） */
    void deleteGroup(Long id);

    /** 群成员列表（合并角色信息：名/描述/头像/模型/系统提示词/温度） */
    List<Map<String, Object>> listMembers(Long groupId);

    /** 拉一个角色进群（body: roleId/modelOverride/randomReplyEnabled/randomProbability/randomContextCount） */
    SysAiTavernMember addMember(Long groupId, Map<String, Object> body);

    /** 移出群成员 */
    void removeMember(Long id);

    /** 更新成员配置（模型覆盖 / 随机发言开关 / 概率 / 上下文条数） */
    SysAiTavernMember updateMember(Long id, Map<String, Object> body);

    /** 群消息历史（按时间正序，条数受群 context_limit 约束由聊天接口读取） */
    List<SysAiTavernMessage> listMessages(Long groupId);
}
