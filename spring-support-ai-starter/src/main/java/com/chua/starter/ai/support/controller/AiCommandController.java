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

        // 暂时返回空列表，等后台技能管理模块实现后再对接
        List<Map<String, Object>> items = new ArrayList<>();

        Map<String, Object> result = new HashMap<>();
        result.put("items", items);
        result.put("total", items.size());

        return ReturnResult.ok(result);
    }
}
