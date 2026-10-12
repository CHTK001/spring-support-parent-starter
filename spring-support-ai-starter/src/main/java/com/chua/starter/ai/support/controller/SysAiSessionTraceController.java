package com.chua.starter.ai.support.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.ai.support.entity.SysAiSession;
import com.chua.starter.ai.support.entity.SysAiSessionTrace;
import com.chua.starter.ai.support.mapper.SysAiSessionMapper;
import com.chua.starter.ai.support.service.SysAiSessionTraceService;
import com.chua.starter.ai.support.util.AiUserContext;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 会话执行轨迹 Controller。
 *
 * <p>供会话顶部的「轨迹」页签回放一次运行的全过程：点事件 + 可配对的区间，
 * 前端据此画带时长的甘特图。</p>
 *
 * @since 4.0.0.51
 * @author CH
 */
@RestController
@RequestMapping("/v2/sessions")
@RequiredArgsConstructor
public class SysAiSessionTraceController {

    /**
     * 默认返回的轨迹条数上限，防止一次拉爆响应体。
     */
    private static final int DEFAULT_LIMIT = 5000;

    /**
     * 会话轨迹 服务
     */
    private final SysAiSessionTraceService traceService;

    /**
     * 会话 映射器
     */
    private final SysAiSessionMapper sessionMapper;

    /**
     * 查询指定会话的执行轨迹，按发生顺序返回。
     *
     * @param sessionId 会话ID
     * @param limit     最多返回条数；不传或 ≤0 时取默认值
     * @return 轨迹列表
     */
    @GetMapping("/{sessionId}/traces")
    public ReturnResult<Map<String, Object>> listTraces(
            @PathVariable String sessionId,
            @RequestParam(required = false) Integer limit) {
        Long id = parseId(sessionId);
        if (id == null) {
            return ReturnResult.ok(emptyResult());
        }
        // 轨迹与会话消息同属私密数据，沿用会话的可见性口径
        if (!canAccessSession(id)) {
            return ReturnResult.ok(emptyResult());
        }
        int max = limit == null || limit <= 0 ? DEFAULT_LIMIT : limit;
        List<SysAiSessionTrace> traces = traceService.listBySession(id, max);
        return ReturnResult.ok(toResult(traces, traces.size()));
    }

    /**
     * 把轨迹实体转成前端甘特图直接可用的结构。
     *
     * <p><b>这里刻意不下发 {@code durationMs}。</b>区间的时长等于「配对的结束行时间 − 起始行时间」，
     * 而起始行不在当前这一行里：结束行自己的 {@code eventTime} 就是结束时刻，拿它做减法必然恒等于 0。
     * 只有按 {@code spanId} 配齐起点与终点才算得出来，而配对规则由消费方决定（前端还区分未闭合区间）。
     * 服务端再算一份就成了第二个真相来源，且必然与前端不一致，故只给原始事件，由消费方配对。</p>
     *
     * @param traces 轨迹列表
     * @param total  总条数
     * @return 响应结构
     */
    private static Map<String, Object> toResult(List<SysAiSessionTrace> traces, int total) {
        List<Map<String, Object>> items = new ArrayList<>();
        for (SysAiSessionTrace t : traces) {
            Map<String, Object> item = new HashMap<>();
            item.put("kind", t.getKind());
            item.put("agentId", t.getAgentId());
            item.put("spanId", t.getSpanId());
            item.put("spanEnd", Boolean.TRUE.equals(t.getSpanEnd()));
            item.put("toolName", t.getToolName());
            item.put("text", t.getText());
            item.put("eventTime", t.getEventTime());
            item.put("spanEndTime", t.getSpanEndTime());
            item.put("seq", t.getSeq());
            items.add(item);
        }
        Map<String, Object> result = new HashMap<>();
        result.put("items", items);
        result.put("total", total);
        return result;
    }

    /**
     * 构造空结果。
     *
     * @return 空结构
     */
    private static Map<String, Object> emptyResult() {
        Map<String, Object> result = new HashMap<>();
        result.put("items", new ArrayList<>());
        result.put("total", 0);
        return result;
    }

    /**
     * 校验当前用户是否有权查看该会话。
     *
     * @param sessionId 会话主键
     * @return 有权返回 true
     */
    private boolean canAccessSession(Long sessionId) {
        if (AiUserContext.canAccessOthersData()) {
            return true;
        }
        LambdaQueryWrapper<SysAiSession> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SysAiSession::getId, sessionId)
                .eq(SysAiSession::getSysUserId, AiUserContext.requireUserId());
        return sessionMapper.selectCount(wrapper) > 0;
    }

    /**
     * 解析会话ID。
     *
     * @param raw 原始字符串
     * @return 解析结果；非法返回 null
     */
    private static Long parseId(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return Long.valueOf(raw.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
