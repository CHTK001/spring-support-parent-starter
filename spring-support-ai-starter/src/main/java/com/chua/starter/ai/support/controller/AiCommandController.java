package com.chua.starter.ai.support.controller;

import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.ai.support.chat.ChatSkillSupport;
import com.chua.starter.ai.support.entity.SysAiMessage;
import com.chua.starter.ai.support.entity.SysAiSession;
import com.chua.starter.ai.support.manage.common.ApiErrorCode;
import com.chua.starter.ai.support.manage.common.ApiException;
import com.chua.starter.ai.support.mapper.SysAiMessageMapper;
import com.chua.starter.ai.support.mapper.SysAiSessionMapper;
import com.chua.starter.ai.support.util.AiUserContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 命令/技能列表控制器
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
@RestController
@RequestMapping("/v2")
@RequiredArgsConstructor
public class AiCommandController {

    private final SysAiMessageMapper messageMapper;

    private final SysAiSessionMapper sessionMapper;

    private final ChatSkillSupport chatSkillSupport;

    /**
     * 获取命令/技能列表
     *
     * @param trigger 触发方式（slash/mention）
     * @param characterId 角色ID
     * @return 命令列表
     */
    @GetMapping("/commands")
    public ReturnResult<Map<String, Object>> commands(
            @RequestParam String trigger,
            @RequestParam(required = false) String characterId) {

        // 对接 ChatSkillSupport：返回技能根目录下已启用的技能，供聊天输入的斜杠/艾特弹层选择
        List<Map<String, Object>> items = new ArrayList<>();
        for (ChatSkillSupport.InstalledSkill skill : chatSkillSupport.skills()) {
            if (!skill.enabled()) {
                continue;
            }
            Map<String, Object> item = new HashMap<>();
            item.put("name", skill.name());
            item.put("label", skill.name());
            item.put("description", skill.description());
            item.put("providerId", "skill");
            items.add(item);
        }

        Map<String, Object> result = new HashMap<>();
        result.put("items", items);
        result.put("total", items.size());

        return ReturnResult.ok(result);
    }

    /**
     * 更新消息内容
     * @param messageId 消息ID，不允许为 null
     * @param body 请求体，包含 content 字段
     * @return ReturnResult 对象
     */
    @PutMapping("/messages/{messageId}")
    public ReturnResult<Map<String, Object>> updateMessage(
            @PathVariable("messageId") String messageId,
            @RequestBody Map<String, Object> body) {
        Long mid = Long.parseLong(messageId);
        String content = (String) body.get("content");
        SysAiMessage message = messageMapper.selectById(mid);
        if (message != null) {
 // 写出口校验：消息随所属会话归属
            requireWritableSession(message.getSysAiSessionId());
            message.setContent(content);
            messageMapper.updateById(message);
        }
        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        return ReturnResult.ok(result);
    }

    /**
     * 写出口归属校验：消息所属会话只认归属人，管理员对他人数据只读。
     *
     * @param sessionId 会话标识
     * @return 校验通过的会话
     */
    private SysAiSession requireWritableSession(Long sessionId) {
        SysAiSession session = sessionId == null ? null : sessionMapper.selectById(sessionId);
        if (session == null) {
            throw new ApiException(ApiErrorCode.NOT_FOUND, "会话不存在");
        }
        if (AiUserContext.isReadonly()) {
            throw new ApiException(ApiErrorCode.FORBIDDEN, "只读账号不允许执行写操作");
        }
        if (!AiUserContext.requireUserId().equals(session.getSysUserId())) {
            throw new ApiException(ApiErrorCode.FORBIDDEN, "管理员对他人数据只读，不可修改或删除");
        }
        return session;
    }
}
