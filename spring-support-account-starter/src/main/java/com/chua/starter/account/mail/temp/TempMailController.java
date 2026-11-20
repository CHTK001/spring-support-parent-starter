package com.chua.starter.account.mail.temp;

import com.chua.common.support.utils.StringUtils;
import com.chua.starter.account.common.ApiErrorCode;
import com.chua.starter.account.common.ApiException;
import com.chua.starter.account.common.ApiResult;
import com.chua.starter.account.manage.store.SysAccount;
import com.chua.starter.common.support.api.annotations.ApiReturnFormatIgnore;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 临时邮箱接口。
 *
 * <p>临时邮箱是账号资产的一种：不新增账号种类，靠
 * {@code config.tempProvider} 标记区分供应商，因此这里只提供
 * 「领取 / 列表 / 销毁 / 供应商清单」，收信直接走统一账号的
 * {@code /api/accounts/{accountId}/mailbox}。</p>
 *
 * <p>多供应商口径：领取时用 {@code provider} 指定供应商（缺省
 * {@code mail-tm}，兼容既有前端调用）；列表合并全部供应商并带
 * {@code config.tempProvider} 标记；销毁按账号上的标记路由到对应服务。</p>
 *
 * @author CH
 * @since 4.0.0.53
 */
@ApiReturnFormatIgnore
@RestController
@RequestMapping("/api/accounts/temp-mail")
@RequiredArgsConstructor
public class TempMailController {

    /**
     * mail.tm 临时邮箱服务
     */
    private final TempMailService tempMailService;

    /**
     * GuerrillaMail 临时邮箱服务
     */
    private final GuerrillaMailService guerrillaMailService;

    /**
     * temp-mail.io 临时邮箱服务
     */
    private final TempMailIoService tempMailIoService;

    /**
     * 领取临时邮箱。
     *
     * @param body 可空；支持 {@code name} 自定义名称、{@code provider} 供应商
     *             （{@code mail-tm} / {@code guerrilla-mail} / {@code temp-mail-io}，
     *             缺省 {@code mail-tm}）
     * @return 落库后的账号（不含明文凭据）
     */
    @PostMapping
    public ApiResult<SysAccount> claim(@RequestBody(required = false) Map<String, String> body) {
        String name = body == null ? null : body.get("name");
        String provider = body == null ? null : body.get("provider");
        return ApiResult.ok(claimBy(provider, name));
    }

    /**
     * 当前用户可见的临时邮箱（全部供应商合并）。
     *
     * @return 临时邮箱清单
     */
    @GetMapping
    public ApiResult<List<SysAccount>> list() {
        List<SysAccount> result = new ArrayList<>();
        result.addAll(tempMailService.list());
        result.addAll(guerrillaMailService.list());
        result.addAll(tempMailIoService.list());
        return ApiResult.ok(result);
    }

    /**
     * 支持的临时邮箱供应商清单。
     *
     * @return 供应商标识列表
     */
    @GetMapping("/providers")
    public ApiResult<List<String>> providers() {
        return ApiResult.ok(List.of(
                TempMailService.PROVIDER,
                GuerrillaMailService.PROVIDER,
                TempMailIoService.PROVIDER));
    }

    /**
     * 销毁临时邮箱（远端账号 + 本地逻辑删除），按账号标记路由到对应供应商服务。
     *
     * @param accountId 账号标识
     * @return 是否删除成功
     */
    @DeleteMapping("/{accountId}")
    public ApiResult<Boolean> destroy(@PathVariable("accountId") String accountId) {
        if (tempMailService.ownsAccount(accountId)) {
            return ApiResult.ok(tempMailService.destroy(accountId));
        }
        if (guerrillaMailService.ownsAccount(accountId)) {
            return ApiResult.ok(guerrillaMailService.destroy(accountId));
        }
        if (tempMailIoService.ownsAccount(accountId)) {
            return ApiResult.ok(tempMailIoService.destroy(accountId));
        }
        throw new ApiException(ApiErrorCode.INVALID_PARAM, "该账号不是临时邮箱");
    }

    /**
     * 按供应商标识路由领取请求。
     *
     * @param provider 供应商标识（可空，缺省 mail-tm）
     * @param name     自定义名称（可空）
     * @return 落库后的账号
     */
    private SysAccount claimBy(String provider, String name) {
        String key = StringUtils.isBlank(provider) ? TempMailService.PROVIDER : provider.trim();
        if (TempMailService.PROVIDER.equalsIgnoreCase(key)) {
            return tempMailService.claim(name);
        }
        if (GuerrillaMailService.PROVIDER.equalsIgnoreCase(key)) {
            return guerrillaMailService.claim(name);
        }
        if (TempMailIoService.PROVIDER.equalsIgnoreCase(key)) {
            return tempMailIoService.claim(name);
        }
        throw new ApiException(ApiErrorCode.INVALID_PARAM, "不支持的临时邮箱供应商：" + key);
    }
}
