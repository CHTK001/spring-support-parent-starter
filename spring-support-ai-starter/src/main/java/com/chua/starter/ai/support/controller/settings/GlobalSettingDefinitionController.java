package com.chua.starter.ai.support.controller.settings;

import com.chua.starter.ai.support.configuration.client.GlobalSettingDefinitions;
import com.chua.starter.ai.support.manage.common.ApiResult;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 全局设置定义控制器。
 *
 * <p>返回所有全局设置服务提供者定义的分组和配置项元数据。
 * 数据来源是启动期预热好的缓存，见 {@link GlobalSettingDefinitions}。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@RestController
@RequestMapping("/v2/global-setting-definitions")
@RequiredArgsConstructor
public class GlobalSettingDefinitionController {

    /**
     * 分组定义缓存（启动期由 GlobalSettingInitializer 预热）
     */
    private final GlobalSettingDefinitions globalSettingDefinitions;

    /**
     * 获取所有分组定义（按 group 去重，合并同名分组的 items）
     *
     * @return 分组列表，每个分组包含 group, groupName, items（配置项列表）
     */
    @GetMapping
    public ApiResult<List<Map<String, Object>>> listDefinitions() {
        // 走启动期预热好的缓存：同一句 SPI 发现在请求线程里返回空（见 GlobalSettingDefinitions 说明），
        // 而启动期 main 线程能正常发现，故请求线程直接读那次的结果。
        return ApiResult.ok(globalSettingDefinitions.definitions());
    }
}
