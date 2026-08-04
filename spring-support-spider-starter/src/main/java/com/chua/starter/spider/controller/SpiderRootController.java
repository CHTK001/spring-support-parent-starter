package com.chua.starter.spider.controller;

import com.chua.common.support.lang.code.ReturnResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 爬虫中心根路径兜底控制器。
 * <p>前端框架（{@code createContextApp}）会在初始化阶段调用
 * {@code GET /spider-api/} 探测服务可用性，如果不存在该 endpoint
 * Spring 会抛 {@code NoResourceFoundException}（500）。
 * 这里提供一个轻量 endpoint 返回模块信息，避免 500。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
@RestController
@RequestMapping("/")
@Tag(name = "Spider Root", description = "爬虫中心根路径 / 健康探测")
public class SpiderRootController {

    @GetMapping({"", "/"})
    @Operation(summary = "爬虫中心健康探测", description = "返回模块基本信息")
    public ReturnResult<Map<String, Object>> root() {
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("module", "spider");
        info.put("status", "UP");
        info.put("version", "4.0.0.42");
        info.put("endpoints", java.util.Arrays.asList(
                "/spider/definitions/**",
                "/spider/data-records/**",
                "/spider/execution-records/**",
                "/spider/executions/**",
                "/spider/flows/**"
        ));
        return ReturnResult.ok(info);
    }
}
