package com.chua.starter.hotsport.support.controller;

import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.hotsport.support.service.ApmQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
* APM 热点观测 REST 接口。
*
* <p>统一前缀 {@code /api/apm}，由前端 vue-support-hotspot-starter 经
* {@code @repo/utils} 的 {@code http().get('/api/apm/...')} 调用。</p>
*
* @author CH
* @since 4.0.0.42
 */
@RestController
@RequestMapping("/api/apm")
@RequiredArgsConstructor
public class ApmController {

    /** APM 查询服务。 */
    private final ApmQueryService apmQueryService;

    /**
    * 概览统计。
    *
    * @return 各类型总量 + 智能体 状态
     */
    @GetMapping("/stats")
    public ReturnResult<Map<String, Object>> stats() {
        return ReturnResult.ok(apmQueryService.stats());
    }

    /**
    * 分页查询传输记录。
    *
    * @param page      页码，默认 1
    * @param size      每页条数，默认 20
    * @param source    源端过滤
    * @param target    目标端过滤
    * @param protocol  协议过滤
    * @param errorOnly 仅看错误
    * @return 分页数据
     */
    @GetMapping("/transmissions")
    public ReturnResult<Map<String, Object>> transmissions(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String source,
            @RequestParam(required = false) String target,
            @RequestParam(required = false) String protocol,
            @RequestParam(required = false) Boolean errorOnly) {
        return ReturnResult.ok(apmQueryService.transmissions(page, size, source, target, protocol, errorOnly));
    }

    /**
    * 查询依赖边。
    *
    * @param size 上限，默认 100
    * @return 依赖边列表
     */
    @GetMapping("/dependencies")
    public ReturnResult<Map<String, Object>> dependencies(
            @RequestParam(defaultValue = "100") int size) {
        List<Map<String, Object>> items = apmQueryService.dependencies(size);
        Map<String, Object> result = Map.of("total", items.size(), "items", items);
        return ReturnResult.ok(result);
    }

    /**
    * 分页查询资源泄漏。
    *
    * @param page         页码，默认 1
    * @param size         每页条数，默认 20
    * @param resourceType 类型过滤
    * @param minAgeMs     最小存活毫秒
    * @return 分页数据
     */
    @GetMapping("/leaks")
    public ReturnResult<Map<String, Object>> leaks(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String resourceType,
            @RequestParam(required = false) Long minAgeMs) {
        return ReturnResult.ok(apmQueryService.leaks(page, size, resourceType, minAgeMs));
    }

    /**
    * 分页查询日志。
    *
    * @param page  页码，默认 1
    * @param size  每页条数，默认 20
    * @param level 级别过滤
    * @return 分页数据
     */
    @GetMapping("/logs")
    public ReturnResult<Map<String, Object>> logs(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String level) {
        return ReturnResult.ok(apmQueryService.logs(page, size, level));
    }

    /**
    * 智能体 实时状态。
    *
    * @return agent 是否激活 + Shell 端口等
     */
    @GetMapping("/agent/status")
    public ReturnResult<Map<String, Object>> agentStatus() {
        Map<String, Object> status = apmQueryService.stats();
        return ReturnResult.ok(status);
    }
}
