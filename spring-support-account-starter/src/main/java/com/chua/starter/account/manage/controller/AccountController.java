package com.chua.starter.account.manage.controller;

import com.chua.starter.account.common.ApiResult;
import com.chua.starter.account.manage.service.ManageAccountService;
import com.chua.starter.account.manage.store.AccountKind;
import com.chua.starter.account.manage.store.SysAccount;
import com.chua.starter.common.support.api.annotations.ApiReturnFormatIgnore;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 统一账号管理接口。
 *
 * <p>账号是设备、模板等必选依赖模块的公共凭据来源，也是账号管理页的主体：</p>
 * <ul>
 *   <li>{@code GET /api/accounts}：账号列表（支持种类与关键词过滤）；</li>
 *   <li>{@code GET /api/accounts/kinds}：受支持的账号种类；</li>
 *   <li>{@code POST /api/accounts}：新增账号；</li>
 *   <li>{@code PUT /api/accounts/{accountId}}：更新账号；</li>
 *   <li>{@code DELETE /api/accounts/{accountId}}：逻辑删除；</li>
 *   <li>{@code POST /api/accounts/{accountId}/verify}：调用校验 SPI 探活。</li>
 * </ul>
 *
 * <p>凭据明文只进不出：写接口接收明文，读接口一律清空 {@code secret}，只回传 {@code hasSecret}。</p>
 *
 * @author CH
 * @since 4.0.0.52
 */
@ApiReturnFormatIgnore
@RestController
@RequestMapping("/api/accounts")
@RequiredArgsConstructor
public class AccountController {

    /**
     * 统一账号服务
     */
    private final ManageAccountService accountService;

    /**
     * 账号列表。
     *
     * @param kind       账号种类（可空）
     * @param keyword    名称 / 编码 / 用户名模糊词（可空）
     * @param channelKey 所属渠道标识（可空；渠道管理页按选中渠道过滤）
     * @return 账号列表
     */
    @GetMapping
    public ApiResult<List<SysAccount>> list(
            @RequestParam(value = "kind", required = false) String kind,
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "channelKey", required = false) String channelKey) {
        return ApiResult.ok(accountService.list(kind, keyword, channelKey));
    }

    /**
     * 受支持的账号种类。
     *
     * @return 种类清单
     */
    @GetMapping("/kinds")
    public ApiResult<List<Map<String, Object>>> kinds() {
        return ApiResult.ok(List.of(
                Map.of("value", AccountKind.API_KEY, "label", "API 密钥"),
                Map.of("value", AccountKind.EMAIL, "label", "邮箱账号"),
                Map.of("value", AccountKind.SMS, "label", "短信账号")));
    }

    /**
     * 账号详情。
     *
     * @param accountId 账号标识
     * @return 账号（不含明文凭据）
     */
    @GetMapping("/{accountId}")
    public ApiResult<SysAccount> get(@PathVariable("accountId") String accountId) {
        return ApiResult.ok(accountService.get(accountId));
    }

    /**
     * 新增账号。
     *
     * @param account 账号内容（{@code secret} 为明文）
     * @return 落库后的账号（不含明文凭据）
     */
    @PostMapping
    public ApiResult<SysAccount> create(@RequestBody SysAccount account) {
        return ApiResult.ok(accountService.create(account));
    }

    /**
     * 更新账号。
     *
     * @param accountId 账号标识
     * @param account   账号内容（{@code secret} 留空或掩码表示不修改）
     * @return 更新后的账号
     */
    @PutMapping("/{accountId}")
    public ApiResult<SysAccount> update(@PathVariable("accountId") String accountId,
            @RequestBody SysAccount account) {
        account.setAccountId(accountId);
        return ApiResult.ok(accountService.update(account));
    }

    /**
     * 删除账号。
     *
     * @param accountId 账号标识
     * @return 是否删除成功
     */
    @DeleteMapping("/{accountId}")
    public ApiResult<Boolean> delete(@PathVariable("accountId") String accountId) {
        return ApiResult.ok(accountService.delete(accountId));
    }

    /**
     * 校验账号凭据。
     *
     * @param accountId 账号标识
     * @return {@code {valid, status, message}}
     */
    @PostMapping("/{accountId}/verify")
    public ApiResult<Map<String, Object>> verify(@PathVariable("accountId") String accountId) {
        return ApiResult.ok(accountService.verify(accountId));
    }
}
