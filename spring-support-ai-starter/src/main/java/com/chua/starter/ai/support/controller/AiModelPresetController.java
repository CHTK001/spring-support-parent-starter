package com.chua.starter.ai.support.controller;

import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.ai.support.manage.mapper.SysAiProviderModelMapper;
import com.chua.starter.ai.support.manage.store.SysAiProviderModel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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

    private final SysAiProviderModelMapper sysAiProviderModelMapper;

    /**
     * 获取模型预设列表
     *
     * @return 模型预设列表
     */
    @GetMapping("/presets")
    public ReturnResult<List<Object>> presets() {
        List<Object> items = new ArrayList<>();
        List<SysAiProviderModel> models = sysAiProviderModelMapper.selectList(null);
        for (SysAiProviderModel model : models) {
            if (Boolean.FALSE.equals(model.getEnabled())) {
                continue;
            }
            Map<String, Object> item = new HashMap<>();
            item.put("model", model.getModel());
            item.put("name", model.getModel());
            item.put("capabilities", model.getCapabilities());
            item.put("description", model.getDescription());
            items.add(item);
        }
        return ReturnResult.ok(items);
    }
}
