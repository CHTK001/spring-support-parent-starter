package com.chua.starter.account.manage.controller;

import com.chua.starter.account.common.ApiResult;
import com.chua.starter.account.manage.service.SysAccountCookieService;
import com.chua.starter.account.manage.store.SysAccountCookie;
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

/**
 * 账号模块 Cookie 池管理接口。
 *
 * <p>供免登录渠道与会话型站点保存登录 Cookie，内容加密落库、接口不回显：</p>
 * <ul>
 *   <li>{@code GET /api/accounts/cookies}：列表；</li>
 *   <li>{@code POST /api/accounts/cookies}：新增；</li>
 *   <li>{@code PUT /api/accounts/cookies/{cookieId}}：更新；</li>
 *   <li>{@code DELETE /api/accounts/cookies/{cookieId}}：逻辑删除。</li>
 * </ul>
 *
 * @author CH
 * @since 4.0.0.52
 */
@ApiReturnFormatIgnore
@RestController
@RequestMapping("/api/accounts/cookies")
@RequiredArgsConstructor
public class AccountCookieController {

    /**
     * Cookie 池服务
     */
    private final SysAccountCookieService cookieService;

    /**
     * Cookie 列表。
     *
     * @param domain  作用域域名（可空）
     * @param source  来源（可空）
     * @param keyword 名称模糊词（可空）
     * @return Cookie 列表
     */
    @GetMapping
    public ApiResult<List<SysAccountCookie>> list(
            @RequestParam(value = "domain", required = false) String domain,
            @RequestParam(value = "source", required = false) String source,
            @RequestParam(value = "keyword", required = false) String keyword) {
        return ApiResult.ok(cookieService.list(domain, source, keyword));
    }

    /**
     * Cookie 详情。
     *
     * @param cookieId Cookie 标识
     * @return Cookie（不含内容）
     */
    @GetMapping("/{cookieId}")
    public ApiResult<SysAccountCookie> get(@PathVariable("cookieId") String cookieId) {
        return ApiResult.ok(cookieService.get(cookieId));
    }

    /**
     * 新增 Cookie。
     *
     * @param input Cookie 内容（{@code cookieValue} 为明文）
     * @return 落库后的 Cookie
     */
    @PostMapping
    public ApiResult<SysAccountCookie> create(@RequestBody SysAccountCookie input) {
        return ApiResult.ok(cookieService.create(input));
    }

    /**
     * 更新 Cookie。
     *
     * @param cookieId Cookie 标识
     * @param input    Cookie 内容（{@code cookieValue} 留空或掩码表示不修改）
     * @return 更新后的 Cookie
     */
    @PutMapping("/{cookieId}")
    public ApiResult<SysAccountCookie> update(@PathVariable("cookieId") String cookieId,
            @RequestBody SysAccountCookie input) {
        input.setCookieId(cookieId);
        return ApiResult.ok(cookieService.update(input));
    }

    /**
     * 删除 Cookie。
     *
     * @param cookieId Cookie 标识
     * @return 是否删除成功
     */
    @DeleteMapping("/{cookieId}")
    public ApiResult<Boolean> delete(@PathVariable("cookieId") String cookieId) {
        return ApiResult.ok(cookieService.delete(cookieId));
    }
}
