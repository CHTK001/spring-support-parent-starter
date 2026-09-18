package com.chua.starter.ai.support.controller;

import com.chua.common.support.lang.code.ReturnResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

/**
 * 插件控制器
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
@RestController
@RequestMapping("/v2/plugins")
@RequiredArgsConstructor
public class AiPluginController {

    /**
    * 获取工具文案
    *
    * @param lang 语言
    * @return 工具文案
    */
    @GetMapping("/tool-displays")
    public ReturnResult<Map<String, Object>> toolDisplays(@RequestParam(required = false) String lang) {
        Map<String, Object> result = new HashMap<>();

        // 返回空对象，前端会使用默认文案
        return ReturnResult.ok(result);
    }
}
