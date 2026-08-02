package com.chua.starter.ai.support.controller.settings;

import com.chua.starter.ai.support.entity.settings.AiGlobalSetting;
import com.chua.starter.ai.support.service.settings.AiGlobalSettingService;
import com.chua.starter.common.support.annotations.Permission;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 全局设置控制器（单条记录），提供查询与更新。
 *
 * @author CH
 * @since 2026-08-03
 */
@RestController
@RequestMapping("/v2/ai/global-settings")
@RequiredArgsConstructor
@Api(tags = "AI 设置 - 全局设置")
public class AiGlobalSettingController {

    /**
     * 全局设置服务
     */
    private final AiGlobalSettingService settingService;

    /**
     * 获取全局设置。
     *
     * @return 全局设置
     */
    @Permission("admin")
    @ApiOperation("获取全局设置")
    @GetMapping
    public AiGlobalSetting get() {
        return settingService.get();
    }

    /**
     * 更新全局设置。
     *
     * @param setting 全局设置
     * @return 更新后的全局设置
     */
    @Permission("admin")
    @ApiOperation("更新全局设置")
    @PutMapping
    public AiGlobalSetting update(@RequestBody AiGlobalSetting setting) {
        return settingService.update(setting);
    }
}