package com.chua.starter.ai.support.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.ai.support.entity.SysAiSession;
import com.chua.starter.ai.support.manage.common.ApiErrorCode;
import com.chua.starter.ai.support.manage.common.ApiException;
import com.chua.starter.ai.support.mapper.SysAiSessionMapper;
import com.chua.starter.ai.support.util.AiUserContext;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 会话管理 Controller
 *
 * @author CH
 */
@RestController
@RequestMapping("/v2/sessions")
@RequiredArgsConstructor
public class SysAiSessionController {

    private final SysAiSessionMapper sessionMapper;

    /**
     * 查询归档会话列表
     */
    @GetMapping("/archived")
    public ReturnResult<Map<String, Object>> listArchived(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long groupId,
            @RequestParam(required = false) Integer skip,
            @RequestParam(required = false) Integer limit) {
        LambdaQueryWrapper<SysAiSession> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SysAiSession::getIsArchived, true);  // 只查询已归档的会话
        // 仅本人归档（管理员在业务数据侧与普通账号同权）
        if (!AiUserContext.canAccessOthersData()) {
            wrapper.eq(SysAiSession::getSysUserId, AiUserContext.requireUserId());
        }
        if (keyword != null && !keyword.isEmpty()) {
            wrapper.like(SysAiSession::getTitle, keyword);
        }
        if (groupId != null) {
            wrapper.eq(SysAiSession::getSysAiModuleCode, groupId);
        }
        wrapper.orderByDesc(SysAiSession::getUpdateTime);

        List<SysAiSession> sessions = sessionMapper.selectList(wrapper);

        List<Map<String, Object>> items = new ArrayList<>();
        for (SysAiSession session : sessions) {
            Map<String, Object> item = new HashMap<>();
            item.put("id", String.valueOf(session.getId()));
            item.put("title", session.getTitle());
            item.put("createdAt", session.getCreateTime());
            item.put("updatedAt", session.getUpdateTime());
            item.put("lastActiveAt", session.getUpdateTime());
            items.add(item);
        }

        Map<String, Object> result = new HashMap<>();
        result.put("items", items);
        result.put("total", items.size());
        return ReturnResult.ok(result);
    }

    /**
     * 归档/取消归档会话
     */
    @PutMapping("/{sessionId}/archive")
    public Map<String, Object> archive(
            @PathVariable String sessionId,
            @RequestBody Map<String, Boolean> body) {
        Boolean archived = body.get("archived");
        
        // 解析 sessionId：数字直接用，UUID 通过标题匹配查找
        Long sessionIdLong = resolveSessionId(sessionId);
        if (sessionIdLong == null) {
            throw new ApiException(ApiErrorCode.NOT_FOUND, "会话不存在");
        }

        SysAiSession session = requireWritableSession(sessionIdLong);
        session.setIsArchived(archived);
        session.setUpdateTime(LocalDateTime.now());
        sessionMapper.updateById(session);
        return Map.of("success", true, "session", session);
    }
    
    /**
     * 解析前端 sessionId 到后端 Long ID。
     *
     * @param sessionId 前端会话 id，数字串或前缀编码串，可为空白
     * @return 后端数值 id，无法解析时返回 null
     */
    private Long resolveSessionId(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            return null;
        }
        // 数字 ID 直接转换
        if (sessionId.matches("\\d+")) {
            return Long.parseLong(sessionId);
        }
        // UUID 通过标题匹配查找（标题格式：guada-xxxxxxxx）
        String titlePrefix = "guada-" + sessionId.substring(0, Math.min(8, sessionId.length()));
        LambdaQueryWrapper<SysAiSession> wrapper = new LambdaQueryWrapper<>();
        wrapper.likeRight(SysAiSession::getTitle, titlePrefix);
        wrapper.orderByDesc(SysAiSession::getCreateTime);
        wrapper.last("LIMIT 1");
        SysAiSession session = sessionMapper.selectOne(wrapper);
        return session != null ? session.getId() : null;
    }

    /**
     * 批量归档/取消归档会话
     * @param body 请求体，不允许为 null
     * @return 结果映射，无数据时为空映射
     */
    @PutMapping("/batch-archive")
    public Map<String, Object> batchArchive(@RequestBody Map<String, Object> body) {
        @SuppressWarnings("unchecked")
        List<Long> sessionIds = (List<Long>) body.get("sessionIds");
        Boolean archived = (Boolean) body.get("archived");
        List<Long> skipped = new ArrayList<>();
        for (Long id : sessionIds) {
            try {
                SysAiSession session = requireWritableSession(id);
                session.setIsArchived(archived);
                session.setUpdateTime(LocalDateTime.now());
                sessionMapper.updateById(session);
            } catch (ApiException e) {
                skipped.add(id);
            }
        }
        return Map.of("success", true, "skipped", skipped);
    }

    /**
     * 写出口归属校验：只认归属人本身，管理员对他人数据只读。
     *
     * @param sessionId 会话标识
     * @return 校验通过的会话
     */
    private SysAiSession requireWritableSession(Long sessionId) {
        SysAiSession session = sessionMapper.selectById(sessionId);
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
