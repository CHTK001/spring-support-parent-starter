package com.chua.starter.collect.controller;

import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.collect.entity.CollectAlertRule;
import com.chua.starter.collect.service.CollectAlertRuleService;
import com.chua.starter.common.support.lang.ReturnPageResult;
import com.chua.starter.mybatis.entity.Query;
import com.chua.starter.mybatis.utils.PageResultUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 采集告警规则接口。
 *
 * @author CH
 * @since 4.0.0.42
 */
@Tag(name = "数据采集-告警规则")
@RestController
@RequestMapping("/v2/collect/alert-rule")
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "plugin.collect", name = "enable", havingValue = "true", matchIfMissing = true)
public class CollectAlertRuleController {

    private final CollectAlertRuleService ruleService;

    @Operation(summary = "规则分页列表")
    @GetMapping("/page")
    public ReturnPageResult<CollectAlertRule> page(@ParameterObject Query<CollectAlertRule> query,
                                                   @ParameterObject CollectAlertRule condition) {
        return PageResultUtils.ok(ruleService.pageRules(query.createPage(), condition));
    }

    @Operation(summary = "查询全部启用的规则")
    @GetMapping("/list")
    public ReturnResult<java.util.List<CollectAlertRule>> listEnabled() {
        return ReturnResult.ok(ruleService.lambdaQuery()
                .eq(CollectAlertRule::getAlertEnabled, 1)
                .list());
    }

    @Operation(summary = "保存或更新规则")
    @PostMapping("/save")
    public ReturnResult<Boolean> save(@RequestBody CollectAlertRule rule) {
        return ReturnResult.ok(ruleService.saveOrUpdateRule(rule));
    }

    @Operation(summary = "删除规则")
    @DeleteMapping("/delete")
    public ReturnResult<Boolean> delete(@RequestParam Integer ruleId) {
        return ReturnResult.ok(ruleService.removeById(ruleId));
    }

    @Operation(summary = "切换启用状态")
    @PostMapping("/toggle")
    public ReturnResult<Boolean> toggle(@RequestParam Integer ruleId,
                                        @RequestParam boolean enabled) {
        return ReturnResult.ok(ruleService.lambdaUpdate()
                .eq(CollectAlertRule::getCollectAlertRuleId, ruleId)
                .set(CollectAlertRule::getAlertEnabled, enabled ? 1 : 0)
                .update());
    }
}