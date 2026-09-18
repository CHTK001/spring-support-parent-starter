package com.chua.starter.ai.support.controller;

import com.chua.common.support.lang.code.ReturnResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;

/**
 * 模型预设控制器
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
@RestController
@RequestMapping("/v2/models")
@RequiredArgsConstructor
public class AiModelPresetController {

    /**
    * 获取模型预设列表
    *
    * @return 模型预设列表
    */
    @GetMapping("/presets")
    public ReturnResult<List<Object>> presets() {
        List<Object> items = new ArrayList<>();

        return ReturnResult.ok(items);
    }
}
