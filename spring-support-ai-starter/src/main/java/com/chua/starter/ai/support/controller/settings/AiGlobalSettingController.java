package com.chua.starter.ai.support.controller.settings;

import com.chua.starter.ai.support.model.AiGlobalSettingDTO;
import com.chua.starter.ai.support.service.settings.AiGlobalSettingService;
import com.chua.starter.common.support.annotations.Permission;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
* 全局设置控制器（单条记录），提供查询与更新。
*
* @since 2026-08-03
* @author CH
 */
@RestController
@RequestMapping("/v2/global-settings")
@RequiredArgsConstructor
@Tag(name = "AI 设置 - 全局设置")
public class AiGlobalSettingController {

    /**
    * 全局设置服务
    */
    private final AiGlobalSettingService settingService;

    /**
    * 获取全局设置。
    *
    * @return 全局设置 DTO
    */
    @Permission("admin")
    @Operation(summary = "获取全局设置")
    @GetMapping
    public AiGlobalSettingDTO get() {
        return settingService.get();
    }

    /**
    * 更新全局设置。
    *
    * @param setting 全局设置 DTO
    * @return 更新后的全局设置 DTO
    */
    @Permission("admin")
    @Operation(summary = "更新全局设置")
    @PutMapping
    public AiGlobalSettingDTO update(@RequestBody AiGlobalSettingDTO setting) {
        return settingService.update(setting);
    }
}
