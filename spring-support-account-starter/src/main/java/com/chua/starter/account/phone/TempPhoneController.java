package com.chua.starter.account.phone;

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
 * 临时手机号接口。
 *
 * <p>临时手机号是账号资产的一种（kind={@code TEMP_PHONE}），这里只提供
 * 「领取 / 列表 / 销毁 / 供应商清单」；收信直接走统一账号的
 * {@code /api/accounts/{accountId}/mailbox}，新短信经统一 SSE 通道
 * {@code /api/accounts/events} 实时推送。</p>
 *
 * <p>多供应商口径：领取时用 {@code provider} 指定来源（{@code temp-phone}
 * 随机号码 / {@code temp-phone-pool} 配置号码池，缺省 {@code temp-phone}）；
 * 列表合并全部来源并带 {@code config.tempPhoneProvider} 标记；销毁按标记路由。</p>
 *
 * @author CH
 * @since 4.0.0.62
 */
@ApiReturnFormatIgnore
@RestController
@RequestMapping("/api/accounts/temp-phone")
@RequiredArgsConstructor
public class TempPhoneController {

    /**
     * 随机号码临时手机号服务
     */
    private final TempPhoneService tempPhoneService;

    /**
     * 号码池临时手机号服务
     */
    private final TempPhonePoolService tempPhonePoolService;

    /**
     * 领取临时手机号（号码服务端生成 / 从池中领取）。
     *
     * @param body 可空；支持 {@code name} 自定义名称、{@code provider} 供应商
     *             （{@code temp-phone} / {@code temp-phone-pool}，缺省 {@code temp-phone}）
     * @return 落库后的账号（不含明文凭据）
     */
    @PostMapping
    public ApiResult<SysAccount> claim(@RequestBody(required = false) Map<String, String> body) {
        String name = body == null ? null : body.get("name");
        String provider = body == null ? null : body.get("provider");
        return ApiResult.ok(claimBy(provider, name));
    }

    /**
     * 当前用户可见的临时手机号（全部供应商合并）。
     *
     * @return 临时手机号清单
     */
    @GetMapping
    public ApiResult<List<SysAccount>> list() {
        List<SysAccount> result = new ArrayList<>();
        result.addAll(tempPhoneService.list());
        result.addAll(tempPhonePoolService.list());
        return ApiResult.ok(result);
    }

    /**
     * 支持的临时手机号供应商清单。
     *
     * @return 供应商标识列表
     */
    @GetMapping("/providers")
    public ApiResult<List<String>> providers() {
        return ApiResult.ok(List.of(TempPhoneService.PROVIDER, TempPhonePoolService.PROVIDER));
    }

    /**
     * 销毁临时手机号（本地逻辑删除），按账号标记路由到对应供应商服务。
     *
     * @param accountId 账号标识
     * @return 是否删除成功
     */
    @DeleteMapping("/{accountId}")
    public ApiResult<Boolean> destroy(@PathVariable("accountId") String accountId) {
        if (tempPhoneService.ownsAccount(accountId)) {
            return ApiResult.ok(tempPhoneService.destroy(accountId));
        }
        if (tempPhonePoolService.ownsAccount(accountId)) {
            return ApiResult.ok(tempPhonePoolService.destroy(accountId));
        }
        throw new ApiException(ApiErrorCode.INVALID_PARAM, "该账号不是临时手机号");
    }

    /**
     * 按供应商标识路由领取请求。
     *
     * @param provider 供应商标识（可空，缺省 temp-phone）
     * @param name     自定义名称（可空）
     * @return 落库后的账号
     */
    private SysAccount claimBy(String provider, String name) {
        String key = StringUtils.isBlank(provider) ? TempPhoneService.PROVIDER : provider.trim();
        if (TempPhoneService.PROVIDER.equalsIgnoreCase(key)) {
            return tempPhoneService.claim(name);
        }
        if (TempPhonePoolService.PROVIDER.equalsIgnoreCase(key)) {
            return tempPhonePoolService.claim(name);
        }
        throw new ApiException(ApiErrorCode.INVALID_PARAM, "不支持的临时手机号供应商：" + key);
    }
}
