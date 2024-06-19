package com.chua.starter.monitor.server.controller;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.chua.common.support.lang.code.ReturnResult;
import com.chua.common.support.utils.StringUtils;
import com.chua.common.support.validator.group.AddGroup;
import com.chua.starter.monitor.server.entity.MonitorProxyConfig;
import com.chua.starter.monitor.server.service.MonitorProxyConfigService;
import com.github.xiaoymin.knife4j.annotations.Ignore;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.validation.BindingResult;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

import static com.chua.common.support.lang.code.ReturnCode.REQUEST_PARAM_ERROR;
import static com.chua.starter.common.support.constant.CacheConstant.SYSTEM;

/**
 * 代理配置
 * @author CH
 * @since 2024/5/13
 */
@RestController
@RequestMapping("v1/proxy/config")
@Tag(name = "代理配置")
@RequiredArgsConstructor
public class MonitorProxyConfigController {

    private final MonitorProxyConfigService monitorProxyConfigService;

    /**
     * 添加数据
     *
     * @param t 实体
     * @return 分页结果
     */
    @ResponseBody
    @Operation(summary = "添加数据")
    @PostMapping("save")
    @CacheEvict(cacheManager = SYSTEM, cacheNames = SYSTEM, key = "#t.proxyId")
    public ReturnResult<Boolean> save(@Validated(AddGroup.class) @RequestBody MonitorProxyConfig t, @Ignore BindingResult bindingResult) {
        if(bindingResult.hasErrors()) {
            return ReturnResult.illegal(REQUEST_PARAM_ERROR, bindingResult.getAllErrors().get(0).getDefaultMessage());
        }

        if(StringUtils.isBlank(t.getProxyId())) {
            return ReturnResult.illegal(REQUEST_PARAM_ERROR, "proxyId不能为空");
        }
        MonitorProxyConfig one = monitorProxyConfigService.getOne(Wrappers.<MonitorProxyConfig>lambdaQuery()
                .eq(MonitorProxyConfig::getProxyId, t.getProxyId())
                .eq(MonitorProxyConfig::getConfigName, t.getConfigName()));
        if(null != one) {
            one.setConfigValue(t.getConfigValue());
            one.setConfigDesc(t.getConfigDesc());
            return ReturnResult.ok(monitorProxyConfigService.updateById(one));
        }
        return ReturnResult.ok(monitorProxyConfigService.save(t));
    }
    /**
     * 查询基础数据
     *
     * @param entity 结果
     * @return 分页结果
     */
    @ResponseBody
    @Operation(summary = "查询基础数据")
    @GetMapping("list")
    @Cacheable(cacheManager = SYSTEM, cacheNames = SYSTEM, key = "#entity.proxyId")
    public ReturnResult<List<MonitorProxyConfig>> list(MonitorProxyConfig entity) {
        return ReturnResult.ok(monitorProxyConfigService.list( Wrappers.<MonitorProxyConfig>lambdaQuery().eq(MonitorProxyConfig::getProxyId, entity.getProxyId())));
    }


}
