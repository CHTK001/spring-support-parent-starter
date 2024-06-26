package com.chua.starter.monitor.server.controller;

import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.chua.common.support.function.Splitter;
import com.chua.common.support.lang.code.ReturnPageResult;
import com.chua.common.support.lang.code.ReturnResult;
import com.chua.common.support.utils.CollectionUtils;
import com.chua.common.support.validator.group.AddGroup;
import com.chua.common.support.validator.group.UpdateGroup;
import com.chua.starter.monitor.server.entity.MonitorProxy;
import com.chua.starter.monitor.server.entity.MonitorProxyLimit;
import com.chua.starter.monitor.server.entity.MonitorProxyLimitLog;
import com.chua.starter.monitor.server.pojo.MonitorProxyLimitLogResult;
import com.chua.starter.monitor.server.service.MonitorProxyLimitLogService;
import com.chua.starter.monitor.server.service.MonitorProxyLimitService;
import com.chua.starter.monitor.server.service.MonitorProxyService;
import com.chua.starter.mybatis.entity.PageRequest;
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

import java.util.Date;
import java.util.Set;

import static com.chua.common.support.lang.code.ReturnCode.REQUEST_PARAM_ERROR;

/**
 * 代理限流器
 * @author CH
 * @since 2024/6/21
 */
@RestController
@RequestMapping("v1/proxy/limit")
@Tag(name = "代理限流器")
@RequiredArgsConstructor
public class MonitorProxyLimitController {
    
    final MonitorProxyLimitService service;
    final MonitorProxyService monitorProxyService;
    final MonitorProxyLimitLogService monitorProxyLimitLogService;
    final TransactionTemplate transactionTemplate;

    /**
     * 分页查询数据
     *
     * @param entity 结果
     * @return 分页结果
     */
    @ResponseBody
    @Operation(summary = "删除")
    @DeleteMapping("log/delete")
    public ReturnResult<Boolean> logDelete(@RequestParam(defaultValue = "3")Integer limitMonth ) {
        return ReturnResult.of(monitorProxyLimitLogService.delete(limitMonth));
    }
    /**
     * 分页查询数据
     *
     * @param entity 结果
     * @return 分页结果
     */
    @ResponseBody
    @Operation(summary = "日志分页查询基础数据")
    @GetMapping("log/page")
    public ReturnPageResult<MonitorProxyLimitLogResult> logPage(PageRequest<MonitorProxyLimitLog> page, MonitorProxyLimitLog entity) {
        Page<MonitorProxyLimitLogResult> tPage = monitorProxyLimitLogService.pageForLog(page.createPage(), entity);
        return PageResultUtils.ok(tPage);
    }
    /**
     * 分页查询数据
     *
     * @param entity 结果
     * @return 分页结果
     */
    @ResponseBody
    @Operation(summary = "分页查询基础数据")
    @GetMapping("page")
    public ReturnPageResult<MonitorProxyLimit> page(PageRequest<MonitorProxyLimit> page, MonitorProxyLimit entity) {
        Page<MonitorProxyLimit> tPage = service.page(page.createPage(), MPJWrappers.<MonitorProxyLimit>lambdaJoin()
                .selectAll(MonitorProxyLimit.class)
                .selectAs(MonitorProxy::getProxyName, MonitorProxyLimit::getProxyName)
                .innerJoin(MonitorProxy.class, MonitorProxy::getProxyId, MonitorProxyLimit::getProxyId)
                .eq(null != entity.getProxyId() , MonitorProxyLimit::getProxyId, entity.getProxyId())
                .like(StringUtils.isNotEmpty(entity.getLimitUrl()), MonitorProxyLimit::getLimitUrl, entity.getLimitUrl())
                .like(StringUtils.isNotEmpty(entity.getProxyName()),MonitorProxy::getProxyName, entity.getProxyName())
                .orderByDesc(MonitorProxyLimit::getCreateTime)
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
            MonitorProxyLimit t = service.getById(CollectionUtils.findFirst(ids));
            service.removeBatchByIds(ids);
            monitorProxyService.refresh(t.getProxyId());
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
    public ReturnResult<Boolean> updateById(@Validated(UpdateGroup.class) @RequestBody MonitorProxyLimit t , @Ignore BindingResult bindingResult) {
        if(bindingResult.hasErrors()) {
            return ReturnResult.illegal(REQUEST_PARAM_ERROR, bindingResult.getAllErrors().get(0).getDefaultMessage());
        }

        MonitorProxyLimit monitorProxyLimit = service.getById(t.getLimitId());
        if(null == monitorProxyLimit) {
            return ReturnResult.illegal(REQUEST_PARAM_ERROR, "主键不能为空");
        }


        return ReturnResult.of(Boolean.TRUE.equals(transactionTemplate.execute(status -> {
            service.updateById(t);
            monitorProxyService.refresh(t.getProxyId());
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
    public ReturnResult<MonitorProxyLimit> save(@Validated(AddGroup.class) @RequestBody MonitorProxyLimit t, @Ignore BindingResult bindingResult) {
        if(bindingResult.hasErrors()) {
            return ReturnResult.illegal(REQUEST_PARAM_ERROR, bindingResult.getAllErrors().get(0).getDefaultMessage());
        }
        return ReturnResult.success(transactionTemplate.execute(status -> {
            t.setCreateTime(new Date());
            service.save(t);
            monitorProxyService.refresh(t.getProxyId());
            return t;
        }));
    }

}
