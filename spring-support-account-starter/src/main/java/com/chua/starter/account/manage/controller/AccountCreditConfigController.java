package com.chua.starter.account.manage.controller;

import com.chua.starter.account.common.ApiResult;
import com.chua.starter.account.manage.service.SysAccountCreditConfigService;
import com.chua.starter.account.manage.store.SysAccountCreditConfig;
import com.chua.starter.common.support.api.annotations.ApiReturnFormatIgnore;
import com.chua.starter.datasearch.credit.model.CreditResult;
import com.chua.starter.datasearch.credit.support.CreditQueries;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 上游余额查询配置接口。
 *
 * <p>网关读不到余额的上游，在这里配置「去哪查、余额在哪个字段」：</p>
 * <ul>
 *   <li>{@code GET /api/accounts/credit-configs}：全部配置；</li>
 *   <li>{@code GET /api/accounts/credit-configs/kinds}：可用的查询实现标识；</li>
 *   <li>{@code GET /api/accounts/credit-configs/{providerId}}：单条；</li>
 *   <li>{@code PUT /api/accounts/credit-configs/{providerId}}：新增 / 覆盖；</li>
 *   <li>{@code DELETE /api/accounts/credit-configs/{providerId}}：删除；</li>
 *   <li>{@code POST /api/accounts/credit-configs/test}：按请求内容试查一次（不落库）。</li>
 * </ul>
 *
 * <p>令牌只进不出：读接口只回 {@code hasToken}，编辑留空或掩码表示不修改。</p>
 *
 * @author CH
 * @since 4.0.0.82
 */
@ApiReturnFormatIgnore
@RestController
@RequestMapping("/api/accounts/credit-configs")
@RequiredArgsConstructor
public class AccountCreditConfigController {

    /**
     * 余额查询配置服务
     */
    private final SysAccountCreditConfigService creditConfigService;

    /**
     * 全部配置。
     *
     * @return 配置列表（令牌已清空）
     */
    @GetMapping
    public ApiResult<List<SysAccountCreditConfig>> list() {
        return ApiResult.ok(creditConfigService.list());
    }

    /**
     * 可用的查询实现标识（前端下拉直接用，不硬编码）。
     *
     * @return 实现标识列表
     */
    @GetMapping("/kinds")
    public ApiResult<List<String>> kinds() {
        return ApiResult.ok(CreditQueries.kinds());
    }

    /**
     * 单条配置。
     *
     * @param providerId 上游 id
     * @return 配置；不存在返回 {@code null}
     */
    @GetMapping("/{providerId}")
    public ApiResult<SysAccountCreditConfig> get(@PathVariable("providerId") String providerId) {
        return ApiResult.ok(creditConfigService.get(providerId));
    }

    /**
     * 新增或覆盖一条配置。
     *
     * @param providerId 上游 id
     * @param config     配置内容（{@code token} 留空或掩码表示保留原值）
     * @return 落库后的配置
     */
    @PutMapping("/{providerId}")
    public ApiResult<SysAccountCreditConfig> save(@PathVariable("providerId") String providerId,
            @RequestBody SysAccountCreditConfig config) {
        return ApiResult.ok(creditConfigService.save(providerId, config));
    }

    /**
     * 删除一条配置。
     *
     * @param providerId 上游 id
     * @return 是否删除成功
     */
    @DeleteMapping("/{providerId}")
    public ApiResult<Boolean> delete(@PathVariable("providerId") String providerId) {
        return ApiResult.ok(creditConfigService.delete(providerId));
    }

    /**
     * 按请求内容试查一次余额（不落库）。
     *
     * @param config 配置内容（{@code providerId} 必填；令牌留空表示用已存的）
     * @return 查询结果
     */
    @PostMapping("/test")
    public ApiResult<CreditResult> test(@RequestBody SysAccountCreditConfig config) {
        return ApiResult.ok(creditConfigService.test(config));
    }
}
