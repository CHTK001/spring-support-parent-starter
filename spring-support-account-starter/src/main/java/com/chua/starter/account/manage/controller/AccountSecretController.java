package com.chua.starter.account.manage.controller;

import com.chua.starter.account.common.ApiResult;
import com.chua.starter.account.manage.service.SysAccountSecretService;
import com.chua.starter.account.manage.store.SysAccountSecret;
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
 * 账号模块密钥库管理接口。
 *
 * <p>密钥是账号 / 设备 / server 共用的底层凭据：</p>
 * <ul>
 *   <li>{@code GET /api/accounts/secrets}：列表；</li>
 *   <li>{@code POST /api/accounts/secrets}：新增；</li>
 *   <li>{@code PUT /api/accounts/secrets/{secretId}}：更新；</li>
 *   <li>{@code DELETE /api/accounts/secrets/{secretId}}：逻辑删除。</li>
 * </ul>
 *
 * <p>Secret 只进不出：读接口只回 {@code hasSecret}，编辑留空表示不修改。</p>
 *
 * @author CH
 * @since 4.0.0.51
 */
@ApiReturnFormatIgnore
@RestController
@RequestMapping("/api/accounts/secrets")
@RequiredArgsConstructor
public class AccountSecretController {

    /**
     * 密钥库服务
     */
    private final SysAccountSecretService secretService;

    /**
     * 密钥列表。
     *
     * @param provider 厂商 / 用途过滤（可空）
     * @param keyword  名称 / AccessKey 模糊词（可空）
     * @return 密钥列表
     */
    @GetMapping
    public ApiResult<List<SysAccountSecret>> list(
            @RequestParam(value = "provider", required = false) String provider,
            @RequestParam(value = "keyword", required = false) String keyword) {
        return ApiResult.ok(secretService.list(provider, keyword));
    }

    /**
     * 密钥详情。
     *
     * @param secretId 密钥标识
     * @return 密钥（不含明文 Secret）
     */
    @GetMapping("/{secretId}")
    public ApiResult<SysAccountSecret> get(@PathVariable("secretId") String secretId) {
        return ApiResult.ok(secretService.get(secretId));
    }

    /**
     * 新增密钥。
     *
     * @param secret 密钥内容（{@code secret} 传明文）
     * @return 落库后的密钥
     */
    @PostMapping
    public ApiResult<SysAccountSecret> create(@RequestBody SysAccountSecret secret) {
        return ApiResult.ok(secretService.create(secret));
    }

    /**
     * 更新密钥。
     *
     * @param secretId 密钥标识
     * @param secret   密钥内容（{@code secret} 留空或掩码表示不修改）
     * @return 更新后的密钥
     */
    @PutMapping("/{secretId}")
    public ApiResult<SysAccountSecret> update(@PathVariable("secretId") String secretId,
            @RequestBody SysAccountSecret secret) {
        secret.setSecretId(secretId);
        return ApiResult.ok(secretService.update(secret));
    }

    /**
     * 删除密钥。
     *
     * @param secretId 密钥标识
     * @return 是否删除成功
     */
    @DeleteMapping("/{secretId}")
    public ApiResult<Boolean> delete(@PathVariable("secretId") String secretId) {
        return ApiResult.ok(secretService.delete(secretId));
    }
}
