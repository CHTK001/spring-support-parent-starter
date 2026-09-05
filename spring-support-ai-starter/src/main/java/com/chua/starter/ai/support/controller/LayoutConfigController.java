package com.chua.starter.ai.support.controller;

import com.chua.starter.ai.support.service.SysAiGlobalSettingService;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 布局识别配置控制器。
 *
 * <p>提供 {@code layout} 分组全局配置的读取与更新（layout/table/reading_order/pose/ocr_link/device）。</p>
 *
 * @since 4.0.0.42
 * @author CH
 */
@RestController
@RequestMapping("/v2/layout/config")
@Tag(name = "AI 布局识别 - 配置管理")
public class LayoutConfigController extends AbstractCapabilityConfigController {

    /**
     * 布局识别配置分组
     */
    private static final String GROUP = "layout";

    public LayoutConfigController(SysAiGlobalSettingService settingService) {
        super(settingService);
    }

    @Override
    protected String group() {
        return GROUP;
    }
}