package com.chua.starter.ai.support.manage.api;

import com.chua.starter.common.support.api.annotations.ApiReturnFormatIgnore;
import com.chua.starter.ai.support.manage.common.ApiResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 登录页 / 前端默认配置控制器（对接 @repo/core 契约）。
 *
 * <p>前端（vue-support-parent-starter/packages/core）登录页挂载时拉取
 * {@code GET /v2/setting/default}，期望返回 {@code { success, data: SettingItem[] }}，
 * 其中 SettingItem 形如 {@code { sysSettingName, sysSettingValue, sysSettingGroup }}。
 * ai-starter 不维护 sys_setting 表，返回空列表即可——前端 {@code mergeDefaultSetting}
 * 对空数组安全跳过，仅消除 404 与「配置拉取失败」降级日志。</p>
 *
 * @since 4.0.0.42
 * @author CH
 */
@ApiReturnFormatIgnore
@RestController
@RequestMapping("/v2/setting")
@Slf4j
public class ApiSettingController {

    /**
     * 默认配置（登录页字段），当前实现返回空列表。
     *
     * @return 空配置项列表
     */
    @GetMapping("/default")
    public ApiResult<List<Map<String, Object>>> getDefaultSetting() {
        return ApiResult.ok(List.of());
    }
}
