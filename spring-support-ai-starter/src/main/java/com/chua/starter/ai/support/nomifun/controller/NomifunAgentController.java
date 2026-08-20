package com.chua.starter.ai.support.nomifun.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collections;
import java.util.List;

/**
 * Nomifun 契约 - Agent 接口（轻量壳）。
 *
 * <p>映射 nomifun 前端 {@code agentConversation.getAvailableAgents} 等命令。
 * 当前返回空目录，后续接入自有 Agent 编排后填充。</p>
 *
 * @since 4.0.0.42
 * @author CH
 */
@Slf4j
@RestController
@RequestMapping("/api/agents")
@RequiredArgsConstructor
public class NomifunAgentController {

    /**
     * Agent 目录（当前为空，等待接入自有编排）。
     *
     * @return Agent 元数据数组
     */
    @GetMapping
    public List<Object> list() {
        return Collections.emptyList();
    }

    /**
     * 刷新自定义 Agent（占位）。
     */
    @PostMapping("/refresh")
    public void refresh() {
        log.debug("[nomifun] agents/refresh invoked (placeholder)");
    }
}
