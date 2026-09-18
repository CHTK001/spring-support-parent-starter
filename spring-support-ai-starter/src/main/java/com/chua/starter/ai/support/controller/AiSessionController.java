package com.chua.starter.ai.support.controller;

import com.chua.common.support.lang.code.ReturnResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 会话控制器
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
@RestController
@RequestMapping("/v2/sessions")
@RequiredArgsConstructor
public class AiSessionController {

    /**
    * 获取侧边栏会话列表
    *
    * @param limit 数量限制
    * @return 会话列表
    */
    @GetMapping("/sidebar")
    public ReturnResult<Map<String, Object>> sidebar(@RequestParam(required = false) Integer limit) {
        List<Map<String, Object>> items = new ArrayList<>();

        Map<String, Object> result = new HashMap<>();
        result.put("items", items);
        result.put("total", items.size());

        return ReturnResult.ok(result);
    }

    /**
    * 获取归档会话列表
    *
    * @param skip 跳过数量
    * @param limit 数量限制
    * @param keyword 搜索关键词
    * @param groupId 分组ID
    * @return 归档会话列表
    */
    @GetMapping("/archived")
    public ReturnResult<Map<String, Object>> archived(
            @RequestParam(required = false) Integer skip,
            @RequestParam(required = false) Integer limit,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String groupId) {

        List<Map<String, Object>> items = new ArrayList<>();

        Map<String, Object> result = new HashMap<>();
        result.put("items", items);
        result.put("total", items.size());

        return ReturnResult.ok(result);
    }
}
