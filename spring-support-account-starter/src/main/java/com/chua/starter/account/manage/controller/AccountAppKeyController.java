package com.chua.starter.account.manage.controller;

import com.chua.starter.account.common.ApiResult;
import com.chua.starter.account.manage.service.SysAccountAppKeyService;
import com.chua.starter.account.manage.store.SysAccountAppKey;
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
 * 账号模块外部访问密钥管理接口。
 *
 * <p>与 AI 的模型计费密钥无关，是账号能力（设备 / 脚本 / 外部系统）的访问凭据：</p>
 * <ul>
 *   <li>{@code GET /api/accounts/app-keys}：列表；</li>
 *   <li>{@code POST /api/accounts/app-keys}：新增，返回一次性明文 Secret；</li>
 *   <li>{@code PUT /api/accounts/app-keys/{appKeyId}}：更新名称 / 状态 / 过期 / 白名单；</li>
 *   <li>{@code POST /api/accounts/app-keys/{appKeyId}/rotate}：轮换 Secret，返回一次性明文；</li>
 *   <li>{@code DELETE /api/accounts/app-keys/{appKeyId}}：逻辑删除。</li>
 * </ul>
 *
 * @author CH
 * @since 4.0.0.52
 */
@ApiReturnFormatIgnore
@RestController
@RequestMapping("/api/accounts/app-keys")
@RequiredArgsConstructor
public class AccountAppKeyController {

    /**
     * 访问密钥服务
     */
    private final SysAccountAppKeyService appKeyService;

    /**
     * 密钥列表。
     *
     * @param keyword 名称 / Key 模糊词（可空）
     * @return 密钥列表
     */
    @GetMapping
    public ApiResult<List<SysAccountAppKey>> list(
            @RequestParam(value = "keyword", required = false) String keyword) {
        return ApiResult.ok(appKeyService.list(keyword));
    }

    /**
     * 新增密钥。
     *
     * @param input 名称、过期时间、IP 白名单、备注
     * @return 密钥（{@code plainSecret} 只在此次响应出现）
     */
    @PostMapping
    public ApiResult<SysAccountAppKey> create(@RequestBody SysAccountAppKey input) {
        return ApiResult.ok(appKeyService.create(input));
    }

    /**
     * 更新密钥属性。
     *
     * @param appKeyId 密钥标识
     * @param input    可变属性
     * @return 更新后的密钥
     */
    @PutMapping("/{appKeyId}")
    public ApiResult<SysAccountAppKey> update(@PathVariable("appKeyId") String appKeyId,
            @RequestBody SysAccountAppKey input) {
        input.setAppKeyId(appKeyId);
        return ApiResult.ok(appKeyService.update(input));
    }

    /**
     * 轮换 Secret。
     *
     * @param appKeyId 密钥标识
     * @return 密钥（{@code plainSecret} 只在此次响应出现）
     */
    @PostMapping("/{appKeyId}/rotate")
    public ApiResult<SysAccountAppKey> rotate(@PathVariable("appKeyId") String appKeyId) {
        return ApiResult.ok(appKeyService.rotate(appKeyId));
    }

    /**
     * 删除密钥。
     *
     * @param appKeyId 密钥标识
     * @return 是否删除成功
     */
    @DeleteMapping("/{appKeyId}")
    public ApiResult<Boolean> delete(@PathVariable("appKeyId") String appKeyId) {
        return ApiResult.ok(appKeyService.delete(appKeyId));
    }
}
