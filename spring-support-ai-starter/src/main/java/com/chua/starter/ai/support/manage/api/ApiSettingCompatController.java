package com.chua.starter.ai.support.manage.api;

import com.chua.starter.ai.support.manage.common.ApiResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 前端设置壳的兼容端点。
 *
 * <p>前端 {@code @repo/core} 在登录后与登录页会探测两个可选的配置端点：
 * {@code GET /v2/setting/list}（codec 等运行时开关）与
 * {@code GET /v2/setting/sso/providers}（第三方登录来源）。
 * 本应用不维护 sys_setting 表、也未接 SSO，若这两个路径不存在会返回 404，
 * 控制台与验收都会出现「配置拉取失败」噪声。</p>
 *
 * <p>与 {@link ApiSettingController} 的区别：后者带
 * {@code @ConditionalOnMissingClass(SysSettingController)}，在内置 common-service
 * 的实现时整个 Bean 不加载；本控制器<b>不带条件</b>，只补这两个缺失路径，
 * 因此不会与 {@code /v2/setting/default} 冲突。</p>
 *
 * @author CH
 * @since 4.0.0.51
 */
@Slf4j
@RestController
@RequestMapping("/v2/setting")
public class ApiSettingCompatController {

    /**
     * 分组配置列表：本应用不维护 sys_setting，返回空列表。
     *
     * @param sysSettingGroup 分组名（可空，不参与过滤）
     * @return 空配置项列表
     */
    @GetMapping("/list")
    public ApiResult<List<Map<String, Object>>> list(
            @RequestParam(value = "sysSettingGroup", required = false) String sysSettingGroup) {
        return ApiResult.ok(List.of());
    }

    /**
     * 第三方登录（SSO）供应商清单：未接 SSO 时返回空数组。
     *
     * @return 空供应商列表
     */
    @GetMapping("/sso/providers")
    public ApiResult<List<Map<String, Object>>> ssoProviders() {
        return ApiResult.ok(List.of());
    }
}
