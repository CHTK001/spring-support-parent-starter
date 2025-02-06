package com.chua.report.server.starter.controller;

import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.chua.common.support.function.Splitter;
import com.chua.common.support.lang.code.ReturnPageResult;
import com.chua.common.support.lang.code.ReturnResult;
import com.chua.common.support.utils.CollectionUtils;
import com.chua.common.support.validator.group.AddGroup;
import com.chua.common.support.validator.group.UpdateGroup;
import com.chua.report.server.starter.entity.MonitorProxy;
import com.chua.report.server.starter.entity.MonitorProxyPluginStatisticServiceDiscovery;
import com.chua.report.server.starter.service.MonitorProxyPluginStatisticServiceDiscoveryService;
import com.chua.report.server.starter.service.MonitorProxyService;
import com.chua.starter.mybatis.entity.Query;
import com.chua.starter.mybatis.utils.PageResultUtils;
import com.github.xiaoymin.knife4j.annotations.Ignore;
import com.github.yulichang.toolkit.MPJWrappers;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.validation.BindingResult;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.Set;

import static com.chua.common.support.lang.code.ReturnCode.REQUEST_PARAM_ERROR;

/**
 * 代理限流器
 * @author CH
 * @since 2024/6/21
 */
@RestController
@RequestMapping("v1/proxy/statistic")
@Tag(name = "代理限流器")
@RequiredArgsConstructor
public class MonitorProxyPluginStatisticServiceDiscoveryController {
    
    final MonitorProxyPluginStatisticServiceDiscoveryService service;
    final MonitorProxyService monitorProxyService;
    final TransactionTemplate transactionTemplate;

    /**
     * 分页查询数据
     *
     * @param entity 结果
     * @return 分页结果
     */
    @ResponseBody
    @Operation(summary = "分页查询基础数据")
    @GetMapping("page")
    public ReturnPageResult<MonitorProxyPluginStatisticServiceDiscovery> page(Query<MonitorProxyPluginStatisticServiceDiscovery> page, MonitorProxyPluginStatisticServiceDiscovery entity) {
        Page<MonitorProxyPluginStatisticServiceDiscovery> tPage = service.page(page.createPage(), MPJWrappers.<MonitorProxyPluginStatisticServiceDiscovery>lambdaJoin()
                .selectAll(MonitorProxyPluginStatisticServiceDiscovery.class)
                .innerJoin(MonitorProxy.class, MonitorProxy::getProxyId, MonitorProxyPluginStatisticServiceDiscovery::getProxyId)
                .eq(null != entity.getProxyId() , MonitorProxyPluginStatisticServiceDiscovery::getProxyId, entity.getProxyId())
                .like(StringUtils.isNotEmpty(entity.getProxyStatisticUrl()), MonitorProxyPluginStatisticServiceDiscovery::getProxyStatisticUrl, entity.getProxyStatisticUrl())
                .like(StringUtils.isNotEmpty(entity.getProxyStatisticHostname()),MonitorProxyPluginStatisticServiceDiscovery::getProxyStatisticHostname, entity.getProxyStatisticName())
        );
        return PageResultUtils.ok(tPage);
    }

    /**
     * 根据主键删除数据
     *
     * @param id 页码
     * @return 分页结果
     */
    @ResponseBody
    @Operation(summary = "删除数据")
    @DeleteMapping("delete")
    public ReturnResult<Boolean> delete(@Parameter(name = "主键") String id) {
        if(null == id) {
            return ReturnResult.illegal(REQUEST_PARAM_ERROR,  "主键不能为空");
        }

        Set<String> ids = Splitter.on(",").trimResults().omitEmptyStrings().splitToSet(id);
        if(ids.isEmpty()) {
            return ReturnResult.ok();
        }

        return ReturnResult.of(Boolean.TRUE.equals(transactionTemplate.execute(status -> {
            MonitorProxyPluginStatisticServiceDiscovery t = service.getById(CollectionUtils.findFirst(ids));
            service.removeBatchByIds(ids);
            monitorProxyService.refresh(String.valueOf(t.getProxyId()));
            return true;
        })));

    }

    /**
     * 根据主键更新数据
     *
     * @param t 实体
     * @return 分页结果
     */
    @ResponseBody
    @Operation(summary = "更新数据")
    @PutMapping("update")
    public ReturnResult<Boolean> updateById(@Validated(UpdateGroup.class) @RequestBody MonitorProxyPluginStatisticServiceDiscovery t , @Ignore BindingResult bindingResult) {
        if(bindingResult.hasErrors()) {
            return ReturnResult.illegal(REQUEST_PARAM_ERROR, bindingResult.getAllErrors().getFirst().getDefaultMessage());
        }

        MonitorProxyPluginStatisticServiceDiscovery monitorProxyLimit = service.getById(t.getProxyStatisticId());
        if(null == monitorProxyLimit) {
            return ReturnResult.illegal(REQUEST_PARAM_ERROR, "主键不能为空");
        }


        return ReturnResult.of(Boolean.TRUE.equals(transactionTemplate.execute(status -> {
            service.updateById(t);
            monitorProxyService.refresh(String.valueOf(t.getProxyId()));
            return true;
        })));
    }

    /**
     * 添加数据
     *
     * @param t 实体
     * @return 分页结果
     */
    @ResponseBody
    @Operation(summary = "添加数据")
    @PostMapping("save")
    public ReturnResult<MonitorProxyPluginStatisticServiceDiscovery> save(@Validated(AddGroup.class) @RequestBody MonitorProxyPluginStatisticServiceDiscovery t, @Ignore BindingResult bindingResult) {
        if(bindingResult.hasErrors()) {
            return ReturnResult.illegal(REQUEST_PARAM_ERROR, bindingResult.getAllErrors().getFirst().getDefaultMessage());
        }
        return ReturnResult.success(transactionTemplate.execute(status -> {
            service.save(t);
            monitorProxyService.refresh(String.valueOf(t.getProxyId()));
            return t;
        }));
    }

}
