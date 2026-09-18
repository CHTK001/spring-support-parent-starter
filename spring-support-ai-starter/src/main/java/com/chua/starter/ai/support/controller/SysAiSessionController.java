package com.chua.starter.ai.support.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chua.starter.ai.support.entity.SysAiSession;
import com.chua.starter.ai.support.mapper.SysAiSessionMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
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
    public List<SysAiSession> listArchived(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long groupId) {
        LambdaQueryWrapper<SysAiSession> wrapper = new LambdaQueryWrapper<>();
        if (keyword != null && !keyword.isEmpty()) {
            wrapper.like(SysAiSession::getTitle, keyword);
        }
        if (groupId != null) {
            wrapper.eq(SysAiSession::getId, groupId);
        }
        wrapper.orderByDesc(SysAiSession::getUpdateTime);
        return sessionMapper.selectList(wrapper);
    }

    /**
    * 归档/取消归档会话
    */
    @PutMapping("/{sessionId}/archive")
    public Map<String, Object> archive(
            @PathVariable Long sessionId,
            @RequestBody Map<String, Boolean> body) {
        Boolean archived = body.get("archived");
        SysAiSession session = sessionMapper.selectById(sessionId);
        if (session != null) {
            session.setUpdateTime(LocalDateTime.now());
            sessionMapper.updateById(session);
        }
        return Map.of("success", true, "session", session);
    }

    /**
    * 批量归档/取消归档会话
    */
    @PutMapping("/batch-archive")
    public Map<String, Object> batchArchive(@RequestBody Map<String, Object> body) {
        @SuppressWarnings("unchecked")
        List<Long> sessionIds = (List<Long>) body.get("sessionIds");
        Boolean archived = (Boolean) body.get("archived");
        for (Long id : sessionIds) {
            SysAiSession session = sessionMapper.selectById(id);
            if (session != null) {
                session.setUpdateTime(LocalDateTime.now());
                sessionMapper.updateById(session);
            }
        }
        return Map.of("success", true, "skipped", List.of());
    }
}
