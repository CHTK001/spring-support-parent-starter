package com.chua.starter.monitor.controller;

import com.chua.common.support.lang.code.ReturnResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collections;
import java.util.List;

/**
 * 服务器代理管理控制器（占位实现以消除前端404）
 *
 * 注意：当前仅提供基础分页与查询占位接口，返回空数据结构，
 * 以保证前端页面正常渲染。后续可根据实际业务补全Service与持久层。
 *
 * 路径前缀：/v1/gen/server/proxy
 * 对应前端：apps/vue-support-monitor-starter/src/api/monitor/gen/server-proxy.ts
 *
 * @author CH
 * @since 2025/08/12
 */
@RestController
@RequestMapping("v1/gen/server/proxy")
@Tag(name = "服务器代理管理", description = "服务器代理分页与查询（占位实现）")
@RequiredArgsConstructor
public class MonitorSysGenServerProxyController {

    /**
     * 分页查询服务器代理列表（占位实现）
     * 前端请求示例：GET /v1/gen/server/proxy/page?page=1&pageSize=1000&params[status]=1
     * 返回结构与前端期望一致：{ data: [], total: 0, page: 1, pageSize: 10 }
     *
     * @param page     页码（默认1）
     * @param pageSize 每页大小（默认10）
     * @param keyword  关键字（可选）
     * @return 分页结果（空数据）
     */
    @Operation(summary = "分页查询服务器代理列表（占位）")
    @GetMapping("page")
    public ReturnResult<PageEnvelope<ServerProxyDto>> page(
            @RequestParam(value = "page", defaultValue = "1") Integer page,
            @RequestParam(value = "pageSize", defaultValue = "10") Integer pageSize,
            @RequestParam(value = "keyword", required = false) String keyword
    ) {
        PageEnvelope<ServerProxyDto> result = new PageEnvelope<>();
        result.setData(Collections.emptyList());
        result.setTotal(0);
        result.setPage(page);
        result.setPageSize(pageSize);
        return ReturnResult.ok(result);
    }

    /**
     * 根据服务器ID查询代理关联列表（占位实现）
     * @param serverId 服务器ID
     * @return 空列表
     */
    @Operation(summary = "根据服务器ID查询代理关联（占位）")
    @GetMapping("server/{serverId}")
    public ReturnResult<List<ServerProxyDto>> listByServer(@PathVariable("serverId") Integer serverId) {
        return ReturnResult.ok(Collections.emptyList());
    }

    /**
     * 根据代理ID查询服务器关联列表（占位实现）
     * @param proxyId 代理ID
     * @return 空列表
     */
    @Operation(summary = "根据代理ID查询服务器关联（占位）")
    @GetMapping("proxy/{proxyId}")
    public ReturnResult<List<ServerProxyDto>> listByProxy(@PathVariable("proxyId") Integer proxyId) {
        return ReturnResult.ok(Collections.emptyList());
    }

    /**
     * 前端所需分页返回结构（简化版）
     */
    @Data
    public static class PageEnvelope<T> {
        private List<T> data;
        private int total;
        private int page;
        private int pageSize;
    }

    /**
     * 服务器代理简单DTO（与前端字段对齐，暂无持久化）
     */
    @Data
    public static class ServerProxyDto {
        private Integer monitorSysGenServerProxyId;
        private String monitorSysGenServerProxyType;
        private String monitorSysGenServerProxyHost;
        private Integer monitorSysGenServerProxyPort;
        private Integer monitorSysGenServerProxyEnabled;
        private Integer monitorSysGenServerProxyStatus;
        private String monitorSysGenServerProxyLastConnectTime;
        private String monitorSysGenServerProxyConnectionError;
        private String monitorSysGenServerProxyRemark;
    }
}

