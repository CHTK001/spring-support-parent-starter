package com.chua.starter.account.phone;

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

import java.util.List;
import java.util.Map;

/**
 * 临时手机号接口。
 *
 * <p>临时手机号是账号资产的一种（kind={@code TEMP_PHONE}），这里只提供
 * 「领取 / 列表 / 销毁」；收信直接走统一账号的
 * {@code /api/accounts/{accountId}/mailbox}，新短信经统一 SSE 通道
 * {@code /api/accounts/events} 实时推送。</p>
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
     * 临时手机号服务
     */
    private final TempPhoneService tempPhoneService;

    /**
     * 领取临时手机号（号码服务端生成）。
     *
     * @param body 可空；支持 {@code name} 自定义名称
     * @return 落库后的账号（不含明文凭据）
     */
    @PostMapping
    public ApiResult<SysAccount> claim(@RequestBody(required = false) Map<String, String> body) {
        String name = body == null ? null : body.get("name");
        return ApiResult.ok(tempPhoneService.claim(name));
    }

    /**
     * 当前用户可见的临时手机号。
     *
     * @return 临时手机号清单
     */
    @GetMapping
    public ApiResult<List<SysAccount>> list() {
        return ApiResult.ok(tempPhoneService.list());
    }

    /**
     * 销毁临时手机号（本地逻辑删除）。
     *
     * @param accountId 账号标识
     * @return 是否删除成功
     */
    @DeleteMapping("/{accountId}")
    public ApiResult<Boolean> destroy(@PathVariable("accountId") String accountId) {
        return ApiResult.ok(tempPhoneService.destroy(accountId));
    }
}
