package com.chua.starter.ai.support.manage.api;

import com.chua.starter.ai.support.manage.common.ApiResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingClass;
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
 * 的实现时整个 Bean 不加载；本控制器<b>同样带该条件</b>——带 common-service
 * 的应用由 SysSettingController 提供真实的 /v2/setting/list 与 /v2/setting/sso/providers，
 * 本控制器让位，避免运行期 Ambiguous mapping（会直接 409「操作状态异常」，
 * 前端 codec 运行时开关就永远读不到）；不带 common-service 的应用（如控制台）
 * 才由本控制器补这两个缺失路径。</p>
 *
 * @author CH
 * @since 4.0.0.51
 */
@Slf4j
@RestController
@RequestMapping("/v2/setting")
@ConditionalOnMissingClass("com.chua.starter.service.controller.SysSettingController")
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
