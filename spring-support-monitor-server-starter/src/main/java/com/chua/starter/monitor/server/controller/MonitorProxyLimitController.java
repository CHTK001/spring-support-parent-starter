package com.chua.starter.monitor.server.controller;

import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.chua.common.support.function.Splitter;
import com.chua.common.support.lang.code.ReturnPageResult;
import com.chua.common.support.lang.code.ReturnResult;
import com.chua.common.support.validator.group.AddGroup;
import com.chua.common.support.validator.group.UpdateGroup;
import com.chua.starter.monitor.server.entity.MonitorProxy;
import com.chua.starter.monitor.server.entity.MonitorProxyLimit;
import com.chua.starter.monitor.server.entity.MonitorProxyLimitLog;
import com.chua.starter.monitor.server.service.MonitorProxyLimitLogService;
import com.chua.starter.monitor.server.service.MonitorProxyLimitService;
import com.chua.starter.mybatis.entity.PageRequest;
import com.chua.starter.mybatis.utils.PageResultUtils;
import com.github.xiaoymin.knife4j.annotations.Ignore;
import com.github.yulichang.toolkit.MPJWrappers;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.redisson.api.RRateLimiter;
import org.redisson.api.RateIntervalUnit;
import org.redisson.api.RateType;
import org.redisson.api.RedissonClient;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.validation.BindingResult;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.Date;
import java.util.List;
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
    final MonitorProxyLimitLogService monitorProxyLimitLogService;
    final TransactionTemplate transactionTemplate;

    final RedissonClient redisson;
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
    public ReturnPageResult<MonitorProxyLimitLog> logPage(PageRequest<MonitorProxyLimitLog> page, MonitorProxyLimitLog entity) {
        Page<MonitorProxyLimitLog> tPage = monitorProxyLimitLogService.page(page.createPage(), MPJWrappers.<MonitorProxyLimitLog>lambdaJoin()
                .selectAll(MonitorProxyLimitLog.class)
                .selectAs(MonitorProxy::getProxyName, MonitorProxyLimitLog::getProxyName)
                .innerJoin(MonitorProxy.class, MonitorProxy::getProxyId, MonitorProxyLimitLog::getLimitLogServerId)
                .eq(null != entity.getLimitLogServerId() , MonitorProxyLimit::getProxyId, entity.getLimitLogServerId())
                .like(StringUtils.isNotEmpty(entity.getLimitLogUrl()), MonitorProxyLimit::getLimitUrl, entity.getLimitLogUrl())
                .like(StringUtils.isNotEmpty(entity.getProxyName()),MonitorProxy::getProxyName, entity.getProxyName())
                .orderByDesc(MonitorProxyLimitLog::getCreateTime)
        );
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


        List<MonitorProxyLimit> monitorProxyLimits = service.listByIds(ids);
        return ReturnResult.of(Boolean.TRUE.equals(transactionTemplate.execute(status -> {
            service.removeBatchByIds(ids);
            for (MonitorProxyLimit monitorProxyLimit : monitorProxyLimits) {
                String limitAddress = monitorProxyLimit.getLimitAddress();
                if(StringUtils.isEmpty(limitAddress)) {
                    redisson.getRateLimiter("monitor:proxy:limit:token:" + monitorProxyLimit.getProxyId() + ":" + monitorProxyLimit.getLimitUrl()).delete();
                } else {
                    redisson.getRateLimiter("monitor:proxy:limit:token:" + monitorProxyLimit.getProxyId() + ":" + monitorProxyLimit.getLimitUrl() + ":" + limitAddress).delete();
                }
            }
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
            MonitorProxyLimit monitorProxyLimit2 = service.getById(t.getLimitId());
            String limitAddress2 = monitorProxyLimit2.getLimitAddress();
            try {
                if(!StringUtils.isEmpty(limitAddress2)) {
                    redisson.getRateLimiter("monitor:proxy:limit:token:" + monitorProxyLimit2.getProxyId() + ":" + monitorProxyLimit2.getLimitUrl() + ":" + limitAddress2).delete();
                }
                redisson.getRateLimiter("monitor:proxy:limit:token:" + monitorProxyLimit2.getProxyId() + ":" + monitorProxyLimit2.getLimitUrl()).delete();
            } catch (Exception ignored) {
            }

            service.updateById(t);
            MonitorProxyLimit monitorProxyLimit1 = service.getById(t.getLimitId());
            String limitAddress = monitorProxyLimit.getLimitAddress();
            RRateLimiter rateLimiter = null;
            if(StringUtils.isEmpty(limitAddress)) {
                rateLimiter = redisson.getRateLimiter("monitor:proxy:limit:token:" + monitorProxyLimit1.getProxyId() + ":" + monitorProxyLimit1.getLimitUrl());
            } else {
                rateLimiter = redisson.getRateLimiter("monitor:proxy:limit:token:" + monitorProxyLimit1.getProxyId() + ":" + monitorProxyLimit1.getLimitUrl() + ":" + limitAddress);
            }
            if(monitorProxyLimit1.getLimitDisable() == 0) {
                rateLimiter.delete();
            } else {
                rateLimiter.trySetRate(RateType.OVERALL, monitorProxyLimit1.getLimitPermitsPerSecond(), 1, RateIntervalUnit.SECONDS);
            }
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
            if(t.getLimitDisable() == 1) {
                RRateLimiter rateLimiter = null;
                String limitAddress = t.getLimitAddress();
                if(StringUtils.isEmpty(limitAddress)) {
                    rateLimiter = redisson.getRateLimiter("monitor:proxy:limit:token:" + t.getProxyId() + ":" + t.getLimitUrl());
                } else {
                    rateLimiter = redisson.getRateLimiter("monitor:proxy:limit:token:" + t.getProxyId() + ":" + t.getLimitUrl() + ":" + limitAddress);
                }
                rateLimiter.trySetRate(RateType.OVERALL, t.getLimitPermitsPerSecond(), 1, RateIntervalUnit.SECONDS);
            }
            return t;
        }));
    }

}
